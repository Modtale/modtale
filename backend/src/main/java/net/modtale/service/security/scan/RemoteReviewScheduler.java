package net.modtale.service.security.scan;

import org.springframework.context.SmartLifecycle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class RemoteReviewScheduler implements SmartLifecycle,AutoCloseable {
    public record Settings(int workers,int pageSize,long pollMillis,long drainMillis) {
        public Settings {if(workers<1 || workers>2 || pageSize<1 || pageSize>64 || pollMillis<100 || pollMillis>60000 || drainMillis<100 || drainMillis>30000)throw new IllegalArgumentException("Invalid remote scheduler limits");}
    }
    public record Status(String state,int active,int buffered,long pages,long processed,long failures,String lastOutcome) {}
    private final RemoteReviewDiscovery discovery;
    private final RemoteReviewBootstrap bootstrap;
    private final Settings settings;
    private final Object lifecycle=new Object(),queueLock=new Object();
    private final CountDownLatch stopping=new CountDownLatch(1);
    private final ArrayDeque<RemoteReviewDiscovery.Candidate> queue=new ArrayDeque<>();
    private final Set<RemoteReviewDiscovery.Candidate> inFlight=new HashSet<>();
    private RemoteReviewDiscovery.Cursor cursor;
    private final AtomicInteger active=new AtomicInteger(),buffered=new AtomicInteger();
    private final AtomicLong pages=new AtomicLong(),processed=new AtomicLong(),failures=new AtomicLong();
    private final Queue<Runnable> callbacks=new ConcurrentLinkedQueue<>();
    private final AtomicBoolean watching=new AtomicBoolean();
    private final Set<Thread> threads=ConcurrentHashMap.newKeySet();
    private volatile String state="NEW",lastOutcome="NONE";
    private volatile ExecutorService executor;
    public RemoteReviewScheduler(RemoteReviewDiscovery discovery,RemoteReviewBootstrap bootstrap,Settings settings) {
        this.discovery=Objects.requireNonNull(discovery);this.bootstrap=Objects.requireNonNull(bootstrap);this.settings=Objects.requireNonNull(settings);
    }
    @Override public void start() {
        synchronized(lifecycle) {
            if("RUNNING".equals(state))return;if(!"NEW".equals(state))throw new IllegalStateException("Closed scheduler cannot restart");
            executor=Executors.newFixedThreadPool(settings.workers(),r->{var t=new Thread(r,"remote-review-poll");t.setDaemon(true);return t;});state="RUNNING";
            try{for(int i=0;i<settings.workers();i++)executor.execute(this::loop);}catch(RuntimeException failure){requestStop();throw failure;}
        }
    }
    private RemoteReviewDiscovery.Candidate next() {
        synchronized(queueLock) {
            if(stopping.getCount()==0)return null;
            if(queue.isEmpty()) {
                var page=discovery.page(cursor,settings.pageSize());
                if(stopping.getCount()==0)return null;
                if(page.candidates().size()>settings.pageSize())throw new IllegalStateException("Oversized discovery page");
                queue.addAll(page.candidates());cursor=page.next();pages.incrementAndGet();buffered.set(queue.size());
            }
            while(!queue.isEmpty()) {var candidate=queue.removeFirst();buffered.set(queue.size());if(inFlight.add(candidate))return candidate;}
            return null;
        }
    }
    private void loop() {
        threads.add(Thread.currentThread());
        try {
            while(stopping.getCount()!=0) {
                long pause=settings.pollMillis();RemoteReviewDiscovery.Candidate candidate=null;
                try {
                    candidate=next();
                    if(candidate!=null) {
                        active.incrementAndGet();var outcome=bootstrap.advance(candidate.projectId(),candidate.versionId(),candidate.attempt(),candidate.requestId(),
                                ()->stopping.getCount()!=0 && !Thread.currentThread().isInterrupted());
                        lastOutcome=outcome.state();processed.incrementAndGet();
                        if(Set.of("RETRY","UNKNOWN").contains(outcome.state())){failures.incrementAndGet();pause=Math.max(5000,pause);}
                    }
                } catch(RuntimeException failure) {if(stopping.getCount()==0)break;failures.incrementAndGet();lastOutcome="POLL_ERROR";pause=Math.max(5000,pause);}
                finally {if(candidate!=null){active.decrementAndGet();synchronized(queueLock){inFlight.remove(candidate);}}}
                if(stopping.await(pause,TimeUnit.MILLISECONDS))break;
            }
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();if(stopping.getCount()!=0){failures.incrementAndGet();lastOutcome="LOOP_INTERRUPTED";requestStop();}}
        catch(Error fatal){failures.incrementAndGet();lastOutcome="LOOP_FAILED";requestStop();throw fatal;}
        finally {threads.remove(Thread.currentThread());}
    }
    private void requestStop() {
        synchronized(lifecycle) {if(stopping.getCount()==0)return;state="STOPPING";stopping.countDown();if(executor!=null)executor.shutdownNow();}
    }
    private boolean stopped() {
        var service=executor;
        if(stopping.getCount()==0 && (service==null || service.isTerminated()) && active.get()==0) {state="STOPPED";return true;}
        return false;
    }
    private boolean awaitStop(long millis)throws InterruptedException {
        var service=executor;if(service!=null)service.awaitTermination(millis,TimeUnit.MILLISECONDS);return stopped();
    }
    @Override public void stop() {
        requestStop();if(threads.contains(Thread.currentThread()))return;
        try {if(!awaitStop(settings.drainMillis()))state="DRAIN_TIMEOUT";}catch(InterruptedException interrupted){Thread.currentThread().interrupt();state="DRAIN_INTERRUPTED";}
    }
    @Override public void stop(Runnable callback) {
        callbacks.add(Objects.requireNonNull(callback));requestStop();watch();
    }
    private void watch() {
        if(!watching.compareAndSet(false,true))return;
        Thread.startVirtualThread(()->{
            try {
                if(!awaitStop(settings.drainMillis()))state="DRAIN_TIMEOUT";
                while(!stopped())awaitStop(1000);
                Runnable callback;while((callback=callbacks.poll())!=null)try{callback.run();}catch(RuntimeException failure){failures.incrementAndGet();}
            } catch(InterruptedException interrupted){Thread.currentThread().interrupt();state="DRAIN_INTERRUPTED";}
            finally {watching.set(false);if(!callbacks.isEmpty())watch();}
        });
    }
    public Status status(){stopped();return new Status(state,active.get(),buffered.get(),pages.get(),processed.get(),failures.get(),lastOutcome);}
    @Override public boolean isRunning(){return !"NEW".equals(state) && !stopped();}
    @Override public boolean isAutoStartup(){return true;}
    @Override public int getPhase(){return Integer.MAX_VALUE;}
    @Override public void close(){stop();if(!stopped())throw new IllegalStateException("Remote scheduler drain incomplete");}
}
