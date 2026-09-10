import sharp from 'sharp';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../', import.meta.url));
const out = path.join(root,'public/assets/news');
const sources = path.join(root,'media/news');
await mkdir(out,{recursive:true});
const data = async (file,mime='image/png') => `data:${mime};base64,${(await readFile(file)).toString('base64')}`;
const logo=await data(path.join(root,'public/assets/logo_light.svg'),'image/svg+xml');
const files=(await readdir(path.join(sources,'mod-icons'))).filter(n=>n.endsWith('.png'));
const icons=Object.fromEntries(await Promise.all(files.map(async n=>[n.replace('.png',''),await data(path.join(sources,'mod-icons',n))])));
const points=(x,y,r)=>Array.from({length:6},(_,i)=>{let a=(i*60-90)*Math.PI/180;return `${(x+r*Math.cos(a)).toFixed(2)},${(y+r*Math.sin(a)).toFixed(2)}`}).join(' ');
const hex=(id,x,y,r,src,opacity=1)=>`<g opacity="${opacity}"><defs><clipPath id="${id}"><polygon points="${points(x,y,r)}"/></clipPath></defs><polygon points="${points(x,y+7,r)}" fill="#040916"/><image x="${x-r}" y="${y-r}" width="${r*2}" height="${r*2}" href="${src}" preserveAspectRatio="xMidYMid slice" clip-path="url(#${id})"/><polygon points="${points(x,y,r)}" fill="none" stroke="url(#edge)" stroke-width="4"/></g>`;
const bg=`<defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#111d33"/><stop offset="1" stop-color="#090f1b"/></linearGradient><linearGradient id="edge" x2="1" y2="1"><stop stop-color="#85b4ff"/><stop offset="1" stop-color="#2563eb"/></linearGradient><radialGradient id="glow"><stop stop-color="#2465d8" stop-opacity=".35"/><stop offset="1" stop-color="#173c7f" stop-opacity="0"/></radialGradient><linearGradient id="fade"><stop stop-color="#0e182a"/><stop offset="1" stop-color="#0e182a" stop-opacity="0"/></linearGradient></defs><rect width="1200" height="630" fill="url(#bg)"/><ellipse cx="930" cy="315" rx="640" ry="600" fill="url(#glow)"/><path d="M0 592H510L560 562" fill="none" stroke="#3b82f6" stroke-width="2"/>`;
const order=['void-scythe','hexcode','autosort','capybara-plushie','voile','levelingcore','ev0s-smokable-herbs','more-weapons-more-armor','infuse','better-repair-kits','implement-weapons','angler-s-almanac','torches','more-armor','dub-s-cannabinol'];
for(const kind of ['modpacks','launcher']){
 let body=bg;
 if(kind==='modpacks'){
  let n=0;
  for(let col=0;col<4;col++)for(let row=0;row<4;row++){
   const x=645+col*174,y=32+row*204+(col%2?102:0);
   const name=order[n%order.length];if(!icons[name])throw new Error(`Missing mod icon: ${name}`);body+=hex(`m${n++}`,x,y,97,icons[name]);
  }
  body+=`<rect width="570" height="630" fill="url(#fade)"/><image x="58" y="68" width="405" height="61" href="${logo}"/><text x="56" y="326" font-family="Inter" font-weight="900" font-size="84" letter-spacing="-4" fill="#f8fafc">Modpacks</text><text x="55" y="440" font-family="Inter" font-weight="900" font-size="115" letter-spacing="-4" fill="#79a8ff">v2</text>`;
 }else{
  const screen=await data(path.join(out,'world-library.jpg'),'image/jpeg');
  body+=`<rect x="567" y="170" width="652" height="413" rx="18" fill="#040916"/><rect x="558" y="156" width="652" height="413" rx="18" fill="#15233b" stroke="url(#edge)" stroke-width="3"/><clipPath id="appscreen"><rect x="564" y="162" width="640" height="400" rx="12"/></clipPath><image x="564" y="162" width="640" height="400" href="${screen}" clip-path="url(#appscreen)"/><image x="58" y="68" width="405" height="61" href="${logo}"/><text x="56" y="334" font-family="Inter" font-weight="900" font-size="88" letter-spacing="-4" fill="#f8fafc">Launcher</text><text x="60" y="393" font-family="Inter" font-weight="700" font-size="27" fill="#9ec4ff">Hytale, made yours.</text>`;
 }
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="2400" height="1260" viewBox="0 0 1200 630">${body}</svg>`;
 await writeFile(path.join(sources,`${kind}-thumbnail.svg`),svg);
 for(const suffix of ['thumbnail','og'])await sharp(Buffer.from(svg)).png().toFile(path.join(out,`${kind}-${suffix}.png`));
 console.log(`${kind}: rebuilt`);
}
