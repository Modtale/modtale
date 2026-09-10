from pathlib import Path
import json, shutil, re, urllib.request
from bs4 import BeautifulSoup
B=Path(__file__).resolve().parents[1];A=B.parents[1]/'frontend/public/assets/news'
posts=[
('01-update-overview','The next chapter of Modtale','Modpacks v2 + Modtale Launcher',None,"Meet Modpacks v2 + the Modtale Launcher.\n\nBuild a pack, give each world its own mods, and bring your setup together in one place. Here's the update in 15 seconds.\n\nExplore both: https://modtale.net/news",'Launch day · lead post'),
('02-build-a-pack','Build it. Share it.','Choose mods, releases, and config defaults.','modpack-creation',"Your modpack, built right on Modtale.\n\nChoose the mods and releases you want, attach per-mod config defaults, and give your setup a home with Modpacks v2.\n\nhttps://modtale.net/news/modpacks-v2",'Day 1 · reply to intro'),
('03-download-to-worlds','Found your next favorite?','Download it. Choose its worlds.','browse-projects',"Find a mod. Download it. Choose where it belongs.\n\nThe Modtale Launcher takes LevelingCore from its project page into your worlds, with the installed version right there in your library.\n\nhttps://modtale.net/launcher",'Day 1 · standalone feature'),
('04-library-search','Different worlds. Different mods.','Search. Enable. Disable.','world-library',"Your worlds don't all need the same mods.\n\nSearch your library, enable a mod in one world, and leave it disabled in another. Enabled and Disabled sections keep each setup easy to follow.\n\nhttps://modtale.net/launcher",'Day 2'),
('05-curseforge-browse','More mods. One library.','Browse CurseForge inside the launcher.','curseforge-mods',"Your next favorite might be on CurseForge.\n\nBrowse its Hytale catalog in the Modtale Launcher, read expanded release notes, download a version, and choose its worlds.\n\nhttps://modtale.net/launcher",'Day 3'),
('06-configs','Tune the world your way.','Edit supported mod configs in the library.','mod-configs',"Less digging through folders. More time tuning your world.\n\nOpen supported mod configs right from the Modtale Launcher library, change a setting, and save it with the right world in view.\n\nhttps://modtale.net/launcher",'Day 4'),
('07-mod-updates','A new release? You are ready.','Spot an update. Install it. Keep playing.','mod-updates',"A new release for a mod you use? The launcher puts the update where you manage your worlds.\n\nHere, Hexcode gets its latest patch and stays enabled in Arcanum.\n\nhttps://modtale.net/launcher",'Day 5'),
('08-curseforge-in-packs','Build beyond one catalog.','Add a specific CurseForge file to your pack.','modpack-curseforge',"Mix Modtale and CurseForge projects in one pack.\n\nPaste a CurseForge file link, review the release, and add it beside your Modtale picks. Packs with CurseForge files install through the launcher.\n\nhttps://modtale.net/news/modpacks-v2",'Day 6'),
('09-shared-list-to-pack','A good setup deserves a home.','Turn a shared mod list into a modpack draft.','modlist-to-pack',"That mod list you've been sharing with friends? It can become a modpack.\n\nChoose Make modpack, review the imported versions and configs, and turn a working setup into your next release.\n\nhttps://modtale.net/news/modpacks-v2",'Day 7'),
('10-sync','Your setup, ready to follow you.','Restore Modtale installs, preferences, and configs.','account-sync',"New computer. Familiar setup.\n\nLoad your saved launcher preferences, Modtale installs, and supported configs from your account. This clip shows the full restore. Your world saves stay on your device.\n\nhttps://modtale.net/launcher",'Day 8'),
('11-wardrobe','Make Hytale a little more you.','Browse looks. Customize. Save.','wardrobe',"A little more you, before you press Play.\n\nBrowse skins in the launcher's Wardrobe, try a hairstyle and colors, then save a look to return to. Preview-only cosmetics stay distinct from what you own.\n\nhttps://modtale.net/launcher",'Day 9'),
('12-open-source','Find something you can build on.','Discover open-source projects with a filter.','open-source',"Looking for open-source Hytale projects?\n\nOpen Filters, switch on Open Source, and explore projects by their declared license. A small addition since June, with a whole lot to discover.\n\nhttps://modtale.net/mods?openSource=true",'Day 11 · since-June catch-up'),
('13-project-galleries','Let your project do the talking.','Explore screenshots in a full gallery.','project-galleries',"Give your project more than a cover image.\n\nModtale's galleries let players explore screenshots up close and jump between a project's best moments without leaving its page. Another addition since June.\n\nhttps://modtale.net/mod/levelingcore",'Day 13 · since-June catch-up')
]
def count(text):
 assert text.isascii(),text
 return len(re.sub(r'https?://\S+','x'*23,text))
data=[]
for key,title,sub,clip,text,slot in posts:
 n=count(text);assert n<=280,(key,n)
 obj=dict(id=key,title=title,subtitle=sub,clip=clip,text=text,characters=n,remaining=280-n,slot=slot,video=f'{key}.mp4',poster=f'{key}.jpg')
 data.append(obj);(B/'twitter'/f'{key}.txt').write_text(text+'\n')
(B/'twitter/posts.json').write_text(json.dumps(data,indent=2)+'\n')
shutil.copy(B.parents[1]/'frontend/public/assets/logo_light.svg',B/'source/logo.svg')
for slug in ['modtale-launcher','modpacks-v2']:
 html=urllib.request.urlopen('http://127.0.0.1:5187/news/'+slug).read().decode();s=BeautifulSoup(html,'html.parser');article=s.find('article');assert article
 for e in article.select('script,style'):e.decompose()
 for e in article.select('a[href]'):
  if e['href'].startswith('/'):e['href']='https://modtale.net'+e['href']
 for fig in article.select('figure'):
  img=fig.find('img');v=fig.find('video');src=img.get('src','') if img else '';name=src.split('/')[-1].split('?')[0]
  if name and (A/name).exists():
   dest=B/'articles/assets';dest.mkdir(exist_ok=True);shutil.copy(A/name,dest/name);img['src']='assets/'+name
  if v:
   mp4=Path(name).stem+'.mp4'
   if (A/mp4).exists():
    shutil.copy(A/mp4,B/'articles/assets'/mp4)
    fig.clear();nv=s.new_tag('video',src='assets/'+mp4,poster='assets/'+name,controls=True,preload='metadata');fig.append(nv)
    cap=s.new_tag('figcaption');cap.string=v.get('aria-label','');fig.append(cap)
 for el in article.select('[style]'):del el['style']
 css='body{background:#0b1220;color:#dce5f4;font:17px/1.7 Inter,system-ui;margin:0 auto;max-width:880px;padding:48px 24px}h1,h2,h3,strong{color:#fff}h1{font-size:46px;line-height:1.15}a{color:#8abbff}figure{margin:32px 0}img,video{width:100%;height:auto;border-radius:16px}figcaption{font-size:14px;color:#9caec6}section{margin:38px 0}@font-face{font-family:Inter;src:url(../source/Inter-Regular.ttf)}'
 out='<!doctype html><meta charset="utf-8"><title>'+s.title.get_text()+'</title><style>'+css+'</style><a href="../index.html">← Media brief</a>'+str(article)
 (B/'articles'/f'{slug}.html').write_text(out)
 lines=[]
 for e in article.select('h1,h2,h3,p,figcaption'):
  text=e.get_text(' ',strip=True);text=re.sub(r'\s+',' ',text)
  lines.append(('#'*int(e.name[1])+' ' if e.name.startswith('h') else '')+text)
 (B/'articles'/f'{slug}.md').write_text('\n\n'.join(lines)+'\n')
print('Prepared',len(data),'posts; lengths:',[x['characters'] for x in data])
