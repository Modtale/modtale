import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'media/social-branding');
await fs.mkdir(out, { recursive: true });
const avatar = (await fs.readFile(path.join(root, 'public/assets/favicon.svg'), 'utf8')).replaceAll('#0f172a', '#f8fafc').replaceAll('#1f2e4c', '#3b82f6');
await fs.writeFile(path.join(out, 'avatar.svg'), avatar);
await sharp(Buffer.from(avatar)).resize(800, 800).png().toFile(path.join(out, 'avatar.png'));
const logo = Buffer.from(await fs.readFile(path.join(root, 'public/assets/logo_light.svg'))).toString('base64');
const hex = (x,y,r) => Array.from({length:6}, (_,i) => `${x+r*Math.cos((i*60-30)*Math.PI/180)},${y+r*Math.sin((i*60-30)*Math.PI/180)}`).join(' ');
const banner = `<svg xmlns="http://www.w3.org/2000/svg" width="1500" height="500" viewBox="0 0 1500 500">
<defs><linearGradient id="bg" x2="1" y2=".4"><stop stop-color="#0b1223"/><stop offset="1" stop-color="#153d82"/></linearGradient><radialGradient id="glow"><stop stop-color="#3b82f6" stop-opacity=".28"/><stop offset="1" stop-color="#3b82f6" stop-opacity="0"/></radialGradient><linearGradient id="facet" x2="1" y2="1"><stop stop-color="#60a5fa" stop-opacity=".13"/><stop offset="1" stop-color="#2563eb" stop-opacity=".02"/></linearGradient></defs>
<path fill="url(#bg)" d="M0 0h1500v500H0z"/>
<ellipse cx="1240" cy="200" rx="650" ry="480" fill="url(#glow)"/>
${[310,250,190].map((r,i)=>`<polygon points="${hex(1400,225,r)}" fill="${i===2?'url(#facet)':'none'}" stroke="#60a5fa" stroke-opacity="${.1+i*.07}" stroke-width="2"/>`).join('')}
<polygon points="${hex(95,40,210)}" fill="none" stroke="#60a5fa" stroke-opacity=".09" stroke-width="2"/>
<image href="data:image/svg+xml;base64,${logo}" x="345" y="185" width="820" height="123.1"/>
</svg>`;
await fs.writeFile(path.join(out, 'banner.svg'), banner);
await sharp(Buffer.from(banner)).png().toFile(path.join(out, 'banner.png'));
