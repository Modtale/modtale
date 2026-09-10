from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,subprocess,shutil,sys
B=Path(__file__).resolve().parents[1];A=B.parents[1]/'frontend/public/assets/news';TMP=B/'source/renders';TMP.mkdir(exist_ok=True)
W,H=1280,1024
bold=lambda n:ImageFont.truetype(str(B/'source/Inter-ExtraBold.ttf'),n)
regular=lambda n:ImageFont.truetype(str(B/'source/Inter-Regular.ttf'),n)
def panel(key,title,sub,footer='MODTALE LAUNCHER',hero=False):
 im=Image.new('RGB',(W,H),'#0b1220');d=ImageDraw.Draw(im)
 for y in range(H):
  t=y/H;d.line((0,y,W,y),fill=(int(17-8*t),int(30-14*t),int(52-22*t)))
 d.line((40,145,1240,145),fill='#2e4e76',width=2)
 logo=Image.open(B/'source/logo.png').convert('RGBA');im.paste(logo,(1020,31),logo)
 d.text((40,24),'MODPACKS V2 + LAUNCHER' if hero else 'MODTALE / FEATURE SPOTLIGHT',font=bold(17),fill='#80b4ff')
 font=bold(40)
 while d.textlength(title,font=font)>1190:font=bold(font.size-1)
 d.text((40,53),title,font=font,fill='#f8fafc');d.text((40,107),sub,font=regular(23),fill='#bfcee4')
 d.text((40,982),'modtale.net',font=bold(19),fill='#b7d3ff')
 tw=d.textlength(footer,font=bold(15));d.text((1240-tw,985),footer,font=bold(15),fill='#7e99bd')
 p=TMP/(key+'.png');im.save(p);return p

def encode(key,source,start,duration,title,sub,out,footer='MODTALE LAUNCHER',hero=False):
 bg=panel(key,title,sub,footer,hero);cmd=['ffmpeg','-v','error','-y','-threads','2']
 if source.suffix in ['.jpg','.png']:cmd+=['-loop','1','-framerate','60','-i',str(source)]
 else:cmd+=['-ss',str(start),'-i',str(source)]
 cmd+=['-loop','1','-framerate','60','-i',str(bg),'-filter_complex_threads','2','-filter_complex','[0:v]scale=1280:800:flags=lanczos,setsar=1,fps=60[demo];[1:v][demo]overlay=0:160:shortest=1,scale=in_range=auto:out_range=tv,setsar=1,format=yuv420p[v]','-map','[v]','-an','-t',str(duration),'-c:v','libx264','-pix_fmt','yuv420p','-color_range','tv','-preset','medium','-crf','20','-maxrate','2400k','-bufsize','4800k','-threads','3','-movflags','+faststart',str(out)]
 subprocess.run(cmd,check=True)

def finish(p):
 subprocess.run(['ffmpeg','-v','error','-y','-ss','0.3','-i',str(p),'-frames:v','1','-threads','2',str(p.with_suffix('.jpg'))],check=True)
 print('Rendered',p.name,flush=True)
posts=json.loads((B/'twitter/posts.json').read_text())
for p in posts:
 if not p['clip']:continue
 source=A/(p['clip']+'.mp4')
 if not source.exists():source=B/'source'/(p['clip']+'.mp4')
 if not source.exists():continue
 out=B/'twitter'/p['video']
 if out.exists():continue
 duration=float(json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(source)]))['format']['duration'])
 footer='MODPACKS V2' if p['clip'] in ['modpack-creation','modlist-to-pack','modpack-curseforge'] else 'SINCE JUNE' if p['clip'] in ['open-source','project-galleries'] else 'MODTALE LAUNCHER'
 encode(p['id'],source,0,duration,p['title'],p['subtitle'],out,footer);finish(out)

# Deliberate editorial excerpts, played at the source's edited 1x rate.
trailer=[
('launcher-play.jpg',0,3,'Meet your next Hytale setup.','Modpacks v2 + the Modtale Launcher'),
('modpack-creation.mp4',5,5,'Build a pack right on Modtale.','Choose mods, releases, and config defaults.'),
('browse-projects.mp4',12,4,'From project to your worlds.','Find a mod. Install it where you want it.'),
('world-library.mp4',7,4,'Your worlds. Your mod choices.','Search, enable, and disable from your library.'),
('curseforge-mods.mp4',15,4,'More mods. One library.','Browse CurseForge and explore release notes.'),
('mod-configs.mp4',6,3.5,'Tune your setup.','Edit supported configs with the right world in view.'),
('account-sync.mp4',11,5,'Bring your setup with you.','Restore Modtale installs, settings, and configs.'),
('wardrobe.mp4',5,4,'Make it a little more you.','Try a look. Make it yours. Save it.'),
('launcher-play.jpg',0,3.5,'Make your next world yours.','Explore Modpacks v2 + the launcher at modtale.net/news')]
intro=[
('browse-projects.mp4',0,3,'Meet the Modtale Launcher.','Discover. Download. Play.'),
('modpack-creation.mp4',20,3,'Meet Modpacks v2.','Build your lineup. Bring your configs.'),
('world-library.mp4',8,3,'Give every world its own setup.','Search, enable, and disable mods.'),
('wardrobe.mp4',7,3,'And make it a little more you.','A wardrobe for your next adventure.'),
('launcher-play.jpg',0,3,'Two updates. One Modtale.','See what is new at modtale.net/news')]
for name,seq,out in [('combined-overview',trailer,B/'discord/combined-overview.mp4'),('intro',intro,B/'twitter/01-update-overview.mp4')]:
 if out.exists():continue
 parts=[];time=0;timeline=[]
 for i,(src,start,dur,title,sub) in enumerate(seq):
  part=TMP/f'{name}-{i}.mp4';encode(f'{name}-{i}',A/src,start,dur,title,sub,part,'MODPACKS V2 + LAUNCHER',True);parts.append(part)
  timeline.append(dict(at=time,duration=dur,source=src,sourceStart=start,title=title));time+=dur
 concat=TMP/f'{name}.concat';concat.write_text(''.join("file '"+str(p)+"'\n" for p in parts))
 subprocess.run(['ffmpeg','-v','error','-y','-f','concat','-safe','0','-i',str(concat),'-c','copy','-movflags','+faststart',str(out)],check=True)
 (B/'source'/f'{name}-edit.json').write_text(json.dumps(timeline,indent=2)+'\n');finish(out)
