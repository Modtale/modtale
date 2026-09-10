from pathlib import Path
import subprocess,json,re,hashlib
from PIL import Image,ImageDraw
B=Path(__file__).resolve().parents[1];report={'videos':[],'tweets':[]}
files=sorted((B/'twitter').glob('*.mp4'))+list((B/'discord').glob('*.mp4'));assert len(files)==14,len(files)
for p in files:
 info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','stream=codec_name,width,height,avg_frame_rate,pix_fmt,sample_aspect_ratio:format=duration,size','-of','json',str(p)]));s=info['streams'][0];f=info['format']
 assert (s['width'],s['height'],s['codec_name'],s['pix_fmt'],s['avg_frame_rate'],s['sample_aspect_ratio'])==(1280,1024,'h264','yuv420p','60/1','1:1'),(p,s)
 assert float(f['duration'])<=140 and int(f['size'])<512_000_000
 subprocess.run(['ffmpeg','-v','error','-xerror','-threads','2','-i',str(p),'-f','null','-'],check=True)
 sheet=Image.new('RGB',(1200,980),'#0b1220');d=ImageDraw.Draw(sheet)
 for i,frac in enumerate([.02,.2,.4,.6,.8,.97]):
  frame=B/'review'/f'{p.stem}-{i}.png';t=float(f['duration'])*frac
  subprocess.run(['ffmpeg','-v','error','-y','-ss',str(t),'-i',str(p),'-frames:v','1','-vf','scale=400:320','-threads','2',str(frame)],check=True)
  im=Image.open(frame);x=i%3*400;y=i//3*490;sheet.paste(im,(x,y+22));d.text((x+10,y+5),f'{p.stem} {t:.1f}s',fill='white');frame.unlink()
 sheet.crop((0,0,1200,840)).save(B/'review'/f'{p.stem}-contact.jpg',quality=94)
 report['videos'].append(dict(file=str(p.relative_to(B)),**info,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),fullDecode='passed'))
 print(p.name,float(f['duration']),flush=True)
for p in json.loads((B/'twitter/posts.json').read_text()):
 text=(B/'twitter'/f"{p['id']}.txt").read_text().strip();assert text==p['text'];assert text.isascii();n=len(re.sub(r'https?://\S+','x'*23,text));assert n<=280 and n==p['characters'];assert (B/'twitter'/p['video']).exists();report['tweets'].append(dict(id=p['id'],weightedLength=n,remaining=280-n))
for article in (B/'articles').glob('*.html'):
 text=article.read_text()
 for src in re.findall(r'(?:src|poster)="(assets/[^"?]+)',text):assert (article.parent/src).exists(),(article,src)
report['articleAssets']='all local references exist';(B/'review/validation.json').write_text(json.dumps(report,indent=2)+'\n');print('All 14 videos, 13 tweets and article assets passed.')
