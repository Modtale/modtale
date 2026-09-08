import React from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import { ProjectCard, ProjectCardSkeleton, ListProjectCardSkeleton, CompactProjectCardSkeleton } from '../../src/modules/project/components/ProjectCard';
import { UserProfile } from '../../src/modules/user/views/UserProfile';
import { ManageProjects } from '../../src/modules/user/views/ManageProjects';
import { FollowingModal } from '../../src/modules/user/components/FollowingModal';
import { ManageOrganization } from '../../src/modules/organization/views/ManageOrganization';
import { PlatformAnalytics } from '../../src/modules/admin/components/PlatformAnalytics';
import { AuditLogs } from '../../src/modules/admin/components/AuditLogs';
import { ReportQueue } from '../../src/modules/admin/components/ReportQueue';
import { ProjectDetails } from '../../src/modules/project/views/ProjectDetails';
import { PROJECT_LOADING_DATA, PROJECT_LOADING_PROSE, PROJECT_LOADING_VERSIONS } from '../../src/modules/project/components/projectLoadingData';
import { MobileProvider } from '../../src/context/MobileContext';
import { SSRProvider } from '../../src/context/SSRContext';
import { DownloadModal } from '../../src/modules/project/components/dialogs/DownloadModal';
import { HistoryModal } from '../../src/modules/project/components/dialogs/HistoryModal';
import { Wiki } from '../../src/modules/project/components/HMWiki';
import { ApiDocs } from '../../src/modules/core/views/ApiDocs';
import { Analytics } from '../../src/modules/user/views/Analytics';
import { VerificationQueue } from '../../src/modules/admin/components/VerificationQueue';
import { Members } from '../../src/modules/organization/tabs/Members';
import { ProjectEditorView } from '../../src/modules/project/views/ProjectEditor';
import { ProjectGallerySkeleton } from '../../src/modules/project/components/ProjectGallerySkeleton';
import { GalleryCarouselViewer } from '../../src/modules/project/components/GalleryCarouselViewer';
import { reportReviewData, verificationReviewData, creatorAnalyticsReviewData, apiDocsReviewData } from './loadingReviewFixtures';
import { api } from '../../src/utils/api';
import type { Project } from '../../src/types';
import '../../src/index.css';

const params = new URLSearchParams(location.search);
const loading = params.get('state') === 'loading';
const screen = params.get('screen') || 'cards';
const view = (params.get('view') || 'grid') as 'grid' | 'list' | 'compact';
const dark = params.get('theme') !== 'light';
document.documentElement.classList.toggle('dark', dark);
const projects = [
 {id:'review-one',slug:'better-adventures',title:'Better Adventures',author:'Adventure Studio',description:'Explore new biomes, discover hidden treasures, and make every journey through Orbis feel like a new adventure.',classification:'PLUGIN',downloadCount:24850,favoriteCount:342,updatedAt:'2026-09-01T12:00:00Z',imageUrl:'/assets/favicon.svg'},
 {id:'review-two',slug:'builders-toolkit',title:'Builder’s Toolkit',author:'Blocksmith',description:'A collection of building tools for your next creation. Place, replace, and shape blocks with precision.',classification:'PLUGIN',downloadCount:16420,favoriteCount:219,updatedAt:'2026-09-02T12:00:00Z',imageUrl:'/assets/favicon.svg'},
 {id:'review-three',slug:'cozy-worlds',title:'Cozy Worlds',author:'Willow',description:'Make yourself at home with new furniture, decorations, and small details for your favorite spaces.',classification:'PLUGIN',downloadCount:9320,favoriteCount:186,updatedAt:'2026-09-03T12:00:00Z',imageUrl:'/assets/favicon.svg'},
] as Project[];
const reviewUser = { id:'review-user',username:'Adventure Studio',avatarUrl:'/assets/favicon.svg',bio:'Building new adventures, one block at a time.',accountType:'USER' as const,likedProjectIds:[],roles:[] };
const reviewProject = {...PROJECT_LOADING_DATA,...projects[0], id:'review-project',slug:'review-project',about:PROJECT_LOADING_PROSE,authorId:reviewUser.id,bannerUrl:undefined,hmWikiEnabled:false,comments:[],galleryImages:[],links:{}};
const pageProjects = Array.from({length:12},(_,i)=>({...projects[i%3],id:'review-'+i,authorId:reviewUser.id,status:'PUBLISHED',versions:[]}));
const orgs = [0,1,2].map(i=>({...reviewUser,id:'org-'+i,username:['Adventure Studio','Builders Collective','World Makers'][i],accountType:'ORGANIZATION',organizationMembers:['review-user','other-1','other-2'].map(userId=>({userId,roleId:'member'})),organizationRoles:[{id:'member',name:'Member',permissions:[]}]}));
api.defaults.adapter = async config => {
 if(loading) return new Promise(()=>{});
 const url=config.url || '';
 let data: any = [];
 if(url.includes('/projects/review-project')) data=reviewProject;
 else 
 if(url.includes('/user/me')) data=reviewUser;
 else if(url.includes('/analytics/creator') || url.includes('/analytics/user') || url.includes('/user/analytics')) data=creatorAnalyticsReviewData;
 else if(url.includes('/members')) data=[reviewUser,{...reviewUser,id:'other-1',username:'Blocksmith'},{...reviewUser,id:'other-2',username:'Willow'}];
 else if(url.includes('/user/profile/')) data=reviewUser;
 else if(url.includes('/creators/')) data={content:screen==='profile'?pageProjects:pageProjects.slice(0,3),totalPages:1,totalElements:12};
 else if(url.includes('/user/orgs')) data=screen==='organizations'?orgs:[];
 else if(url.includes('/following')) data=[reviewUser,{...reviewUser,id:'r2',username:'Blocksmith'},{...reviewUser,id:'r3',username:'Willow'}];
 else if(url.includes('/contributed')) data={content:[]};
 else if(url.includes('/analytics/platform')) data={totalDownloads:24850,totalViews:74392,totalNewUsers:342,totalNewProjects:128,...Object.fromEntries(['downloadsChart','apiDownloadsChart','viewsChart','newProjectsChart','newUsersChart','newOrgsChart'].map((key,k)=>[key,Array.from({length:65},(_,i)=>({date:new Date(Date.UTC(2026,0,i+1)).toISOString().slice(0,10),count:Math.round(100+Math.sin(i*.5)*25+i*(k+1))}))]))};
 else if(url.includes('/admin/logs')) data={content:[0,1,2,3,4].map(i=>({id:'log-'+i,action:'PROJECT_UPDATED',adminUsername:'Moderator',targetId:'review-'+i,details:'Updated project metadata',timestamp:'2026-09-07T12:00:00Z'})),totalPages:1,totalElements:5};
 return {data,status:200,statusText:'OK',headers:{},config};
};
const realFetch = window.fetch.bind(window);
window.fetch = (input, init) => String(input).includes('/docs/openapi') ? (loading ? new Promise(()=>{}) : Promise.resolve(new Response(JSON.stringify(apiDocsReviewData),{status:200,headers:{'Content-Type':'application/json'}}))) : realFetch(input,init);
const SkeletonCard = view === 'list' ? ListProjectCardSkeleton : view === 'compact' ? CompactProjectCardSkeleton : ProjectCardSkeleton;
function Review() {
 if(screen==='api-docs') return <ApiDocs/>;
 if(screen==='editor') return <Routes><Route path="/mod/:id/edit" element={<ProjectEditorView currentUser={{...reviewUser,roles:['ADMIN']}} onShowStatus={()=>{}}/>}/></Routes>;
 if(screen==='gallery') return <div className="fixed inset-0 flex items-center justify-center p-4 bg-slate-950/80"><div className="relative w-full max-w-6xl">{loading?<ProjectGallerySkeleton isInline/>:<GalleryCarouselViewer images={[1,2,3].map(index=>({url:'/assets/favicon.svg#'+index,caption:'Gallery image caption'}))} title="Project" autoAdvance={false} activeIndex={0} className="mb-0 overflow-hidden rounded-2xl border border-blue-200 bg-slate-50 shadow-xl shadow-blue-950/20 dark:border-blue-400/20 dark:bg-[#0B1120]" mediaClassName="relative aspect-video max-h-[calc(90dvh-8rem)] bg-slate-200 outline-none dark:bg-slate-950"/>}</div></div>;
 if(screen==='project') return <SSRProvider data={loading?null:reviewProject} initialPath="/mod/review-project"><Routes><Route path="/mod/:id" element={<ProjectDetails currentUser={null} isLiked={()=>false} onToggleFavorite={()=>{}} onDownload={()=>{}} downloadedSessionIds={new Set()} onRefresh={async()=>{}}/>}/></Routes></SSRProvider>;
 if(screen==='downloads') return <DownloadModal show loading={loading} onClose={()=>{}} versionsByGame={{'2026.01.17':PROJECT_LOADING_VERSIONS}} orderedGameVersions={['2026.01.17']} onDownload={()=>{}} showExperimental={false} onToggleExperimental={()=>{}} onViewHistory={()=>{}}/>;
 if(screen==='history') return <HistoryModal show loading={loading} onClose={()=>{}} history={PROJECT_LOADING_VERSIONS} showExperimental={false} onToggleExperimental={()=>{}} onDownload={()=>{}} hasStableVersions />;
 if(screen==='wiki') return <main className="max-w-4xl mx-auto p-12"><Wiki wikiLoading={loading} wikiError={false} wikiData={{mod:{name:'Better Adventures',index:{slug:'index'}},content:{title:'Getting started',content:PROJECT_LOADING_PROSE}}} mod={reviewProject}/></main>;
 if(screen==='profile') return <Routes><Route path="/creator/:id" element={<UserProfile currentUser={null} onBack={()=>{}} likedModIds={[]} onToggleFavorite={()=>{}}/>}/></Routes>;
 if(screen==='following') return <FollowingModal userId={reviewUser.id} onClose={()=>{}}/>;
 if(screen!=='cards') return <main className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-8"><div className="bg-white/90 dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-3xl p-8">
 {screen==='reports'?<ReportQueue reports={loading?[]:reportReviewData} loadingReports={loading} onRefresh={()=>{}} canResolve/>:screen==='verification'?<VerificationQueue pendingProjects={loading?[]:verificationReviewData} loadingQueue={loading} loadingReview={false} onReview={()=>{}}/>:screen==='creator-analytics'?<Analytics/>:screen==='members'?<Members org={orgs[0] as any} currentUser={reviewUser} showStatus={()=>{}} onMemberRemoved={()=>{}}/>:screen==='managed-projects'?<ManageProjects user={reviewUser}/>:screen==='organizations'?<ManageOrganization user={reviewUser}/>:screen==='platform-analytics'?<PlatformAnalytics/>:screen==='audit-logs'?<AuditLogs/>:null}
 </div></main>;
 return <main className="browse-project-results max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-12">
  <div className="flex items-center justify-between mb-8"><div><h1 className="text-3xl font-black text-slate-900 dark:text-white">Discover mods</h1><p className="mt-2 text-slate-500">Find your next adventure.</p></div><span className="text-sm text-slate-500">{view}</span></div>
  <div data-review-content className={view==='grid'?'browse-project-grid':view==='compact'?'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4':'space-y-4 mt-4'}>
  {projects.map(project => <div key={project.id} data-review-item>{loading ? <SkeletonCard /> : <ProjectCard project={project} viewStyle={view} isFavorite={false} isLoggedIn={false} onToggleFavorite={()=>{}} disableNavigation />}</div>)}
  </div>
 </main>;
}
createRoot(document.getElementById('root')!).render(<HelmetProvider><MemoryRouter initialEntries={[screen==='profile'?'/creator/review-user':screen==='project'?'/mod/review-project':screen==='editor'?'/mod/review-project/edit':'/']}><MobileProvider><Review /></MobileProvider></MemoryRouter></HelmetProvider>);
