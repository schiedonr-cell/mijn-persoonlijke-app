(()=>{
'use strict';
const CLIENT_ID='829151315917-8rit0pd97ol16qbnk2i141cr7gffnirb.apps.googleusercontent.com';
const SCOPE='https://www.googleapis.com/auth/calendar.events.readonly';
const TOKEN_KEY='mijnPersoonlijkeAppCalendarTokenV1';
const CONSENT_KEY='mijnPersoonlijkeAppCalendarConsentV1';
const EVENTS_KEY='mijnPersoonlijkeAppCalendarEventsV1';
const APP_STATE_KEY='mijnPersoonlijkeAppV1';
let tokenClient=null,token='',expiresAt=0,loading=false;

const esc=v=>String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
const valid=()=>Boolean(token&&expiresAt>Date.now()+30000);
const dateKey=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};

function readToken(){
  try{
    const s=JSON.parse(localStorage.getItem(TOKEN_KEY)||'{}');
    if(s.token&&Number(s.expiresAt||0)>Date.now()+30000){token=s.token;expiresAt=Number(s.expiresAt);}
    else localStorage.removeItem(TOKEN_KEY);
  }catch{}
}
function saveToken(t,seconds){
  token=t||'';
  expiresAt=Date.now()+(Math.max(60,Number(seconds||3600)-60)*1000);
  try{localStorage.setItem(TOKEN_KEY,JSON.stringify({token,expiresAt}));localStorage.setItem(CONSENT_KEY,'1');}catch{}
}
function clearToken(){token='';expiresAt=0;try{localStorage.removeItem(TOKEN_KEY);}catch{}}
function readEvents(){try{const s=JSON.parse(localStorage.getItem(EVENTS_KEY)||'{}');return s.date===dateKey()&&Array.isArray(s.items)?s:null;}catch{return null;}}
function saveEvents(items){try{localStorage.setItem(EVENTS_KEY,JSON.stringify({date:dateKey(),savedAt:Date.now(),items}));}catch{}}

function style(){if(document.getElementById('calendarIntegrationStyles'))return;const s=document.createElement('style');s.id='calendarIntegrationStyles';s.textContent=`
.calendar-card{padding:16px 18px}.calendar-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:10px}.calendar-title-wrap{display:flex;align-items:center;gap:10px;min-width:0}.calendar-icon{width:38px;height:38px;border-radius:13px;background:var(--accent-soft);color:var(--accent-strong);display:grid;place-items:center;flex:0 0 auto}.calendar-card h3{margin:0 0 2px;font-size:18px}.calendar-sub,.calendar-message,.calendar-empty,.calendar-note{color:var(--muted);font-size:12px;line-height:1.4}.calendar-list{display:grid;gap:8px}.calendar-event{display:grid;grid-template-columns:74px 1fr;gap:10px;padding:10px 11px;border:1px solid var(--line);border-radius:15px;background:var(--surface-soft)}.calendar-time{font-weight:900;color:var(--accent-strong);font-size:13px}.calendar-event-title{font-weight:800}.calendar-event-location{color:var(--muted);font-size:12px;margin-top:2px}.calendar-connect{min-height:42px;padding:9px 12px;border:0;border-radius:13px;background:var(--accent);color:#fff;font-weight:850;cursor:pointer}.calendar-refresh{width:40px;height:40px;border:0;border-radius:12px;background:var(--accent-soft);color:var(--accent-strong);font-size:19px;font-weight:900;cursor:pointer}.calendar-connect:disabled,.calendar-refresh:disabled{opacity:.55}.calendar-note{margin-top:8px}.week-recipes-list{display:grid;gap:8px;margin-top:12px}.week-recipe-row{width:100%;border:1px solid var(--line);border-radius:16px;background:var(--surface-soft);padding:11px 12px;display:grid;grid-template-columns:56px 40px 1fr;gap:10px;align-items:center;text-align:left;color:var(--text)}button.week-recipe-row{cursor:pointer}.week-recipe-day{font-size:11px;font-weight:900;color:var(--accent-strong);line-height:1.2}.week-recipe-emoji{font-size:26px;text-align:center}.week-recipe-name{font-weight:850;line-height:1.25}.week-recipe-meta{font-size:11px;color:var(--muted);margin-top:3px}.week-recipes-empty{color:var(--muted);font-size:13px;line-height:1.45;margin-top:10px}@media(max-width:390px){.calendar-event{grid-template-columns:64px 1fr}.calendar-connect{font-size:12px}.week-recipe-row{grid-template-columns:50px 36px 1fr;padding:10px}}
`;document.head.appendChild(s)}
const icon='<svg width="21" height="21" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8 3v3M16 3v3M4 9h16M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>';
function ensure(){const area=document.getElementById('dayOpenArea');if(!area)return null;let card=document.getElementById('calendarTodayCard');if(!card){card=document.createElement('div');card.id='calendarTodayCard';card.className='section-card calendar-card';area.prepend(card);}return card;}
function head(action,sub='Google Agenda · alleen-lezen'){return `<div class="calendar-heading"><div class="calendar-title-wrap"><div class="calendar-icon">${icon}</div><div><h3>Afspraken</h3><p class="calendar-sub">${sub}</p></div></div>${action}</div>`;}
function time(e){if(e?.start?.date&&!e?.start?.dateTime)return'Hele dag';if(!e?.start?.dateTime)return'';const f=new Intl.DateTimeFormat('nl-NL',{hour:'2-digit',minute:'2-digit'}),a=new Date(e.start.dateTime),b=e.end?.dateTime?new Date(e.end.dateTime):null;return b?`${f.format(a)}–${f.format(b)}`:f.format(a);}
function itemsHtml(items){const v=(items||[]).filter(e=>e.status!=='cancelled');return v.length?v.map(e=>`<div class="calendar-event"><div class="calendar-time">${esc(time(e))}</div><div><div class="calendar-event-title">${esc(e.summary||'Afspraak')}</div>${e.location?`<div class="calendar-event-location">${esc(e.location)}</div>`:''}</div></div>`).join(''):'<div class="calendar-empty">Geen afspraken voor vandaag.</div>';}
function renderDisconnected(msg=''){const c=ensure();if(!c)return;const cached=readEvents();c.innerHTML=head('<button class="calendar-connect" id="calendarConnectButton" type="button">Agenda koppelen</button>')+(cached?`<div class="calendar-list">${itemsHtml(cached.items)}</div><div class="calendar-note">Laatst bijgewerkt om ${new Intl.DateTimeFormat('nl-NL',{hour:'2-digit',minute:'2-digit'}).format(new Date(cached.savedAt||Date.now()))}.</div>`:`<div class="calendar-message">${esc(msg||'Koppel je Google Agenda om afspraken van vandaag te zien.')}</div>`);document.getElementById('calendarConnectButton')?.addEventListener('click',connect);}
function renderConnected(){const c=ensure();if(!c)return;c.innerHTML=head('<button class="calendar-refresh" id="calendarRefreshButton" type="button" aria-label="Agenda vernieuwen">↻</button>','Vandaag · alleen-lezen')+'<div class="calendar-list" id="calendarEventList"><div class="calendar-message">Afspraken laden…</div></div>';document.getElementById('calendarRefreshButton')?.addEventListener('click',()=>load(true));}
function show(items){const list=document.getElementById('calendarEventList');if(list)list.innerHTML=itemsHtml(items);}
function gis(){if(window.google?.accounts?.oauth2)return Promise.resolve();return new Promise((ok,no)=>{const old=document.querySelector('script[data-google-identity-calendar]');if(old){old.addEventListener('load',ok,{once:true});old.addEventListener('error',no,{once:true});return;}const s=document.createElement('script');s.src='https://accounts.google.com/gsi/client';s.async=true;s.defer=true;s.dataset.googleIdentityCalendar='1';s.onload=ok;s.onerror=no;document.head.appendChild(s);});}
async function client(){await gis();if(tokenClient)return tokenClient;tokenClient=google.accounts.oauth2.initTokenClient({client_id:CLIENT_ID,scope:SCOPE,callback:r=>{if(r?.error){renderDisconnected('Koppelen is niet gelukt. Probeer opnieuw.');return;}saveToken(r.access_token,r.expires_in);renderConnected();load();},error_callback:()=>renderDisconnected('Google Agenda kon niet worden geopend. Probeer opnieuw.')});return tokenClient;}
async function connect(){const b=document.getElementById('calendarConnectButton');if(b){b.disabled=true;b.textContent='Even wachten…';}try{const c=await client();let known=false;try{known=localStorage.getItem(CONSENT_KEY)==='1';}catch{}c.requestAccessToken({prompt:known?'':'consent'});}catch{renderDisconnected('Google Agenda kon niet worden gestart.');}}
async function load(force=false){if(loading)return;if(!valid()){clearToken();renderDisconnected('Je agenda-koppeling moet worden vernieuwd.');return;}loading=true;const refresh=document.getElementById('calendarRefreshButton');if(refresh)refresh.disabled=true;try{const a=new Date();a.setHours(0,0,0,0);const b=new Date(a);b.setDate(b.getDate()+1);const q=new URLSearchParams({timeMin:a.toISOString(),timeMax:b.toISOString(),singleEvents:'true',orderBy:'startTime',maxResults:'25'});if(force)q.set('_',String(Date.now()));const r=await fetch(`https://www.googleapis.com/calendar/v3/calendars/primary/events?${q}`,{headers:{Authorization:`Bearer ${token}`},cache:'no-store'});if(r.status===401||r.status===403){clearToken();renderDisconnected('Je agenda-koppeling moet worden vernieuwd.');return;}if(!r.ok)throw new Error();const items=(await r.json()).items||[];saveEvents(items);show(items);}catch{const cached=readEvents();if(cached)show(cached.items);else{const list=document.getElementById('calendarEventList');if(list)list.innerHTML='<div class="calendar-message">Afspraken konden nu niet worden geladen.</div>';}}finally{loading=false;if(refresh)refresh.disabled=false;}}

function readAppState(){try{return JSON.parse(localStorage.getItem(APP_STATE_KEY)||'{}');}catch{return {};}}
function weekDayLabel(key){try{return new Intl.DateTimeFormat('nl-NL',{weekday:'short',day:'numeric',month:'short'}).format(new Date(`${key}T12:00:00`));}catch{return key||'';}}
function recipeLibraryCard(id){return [...document.querySelectorAll('#recipeLibrary [data-recipe]')].find(el=>el.dataset.recipe===id)?.closest('.library-card')||null;}
function weekRecipeHtml(item,state){
  if(item.recipeId){
    const source=recipeLibraryCard(item.recipeId),name=source?.querySelector('.library-name')?.textContent?.trim()||'Recept',emoji=source?.querySelector('.library-emoji')?.textContent?.trim()||'🍽️',meta=source?.querySelector('.library-meta')?.textContent?.trim()?.split('\n')[0]||'Tik om het recept te openen';
    return `<button type="button" class="week-recipe-row" data-recipe="${esc(item.recipeId)}"><div class="week-recipe-day">${esc(weekDayLabel(item.date))}</div><div class="week-recipe-emoji">${esc(emoji)}</div><div><div class="week-recipe-name">${esc(name)}</div><div class="week-recipe-meta">${esc(meta)}</div></div></button>`;
  }
  if(item.pantryMealId){
    const meal=(state.pantry||[]).find(p=>p.id===item.pantryMealId),name=meal?.name||'Maaltijd uit voorraad',where=meal?.location||'Voorraad';
    return `<div class="week-recipe-row"><div class="week-recipe-day">${esc(weekDayLabel(item.date))}</div><div class="week-recipe-emoji">${where==='Vriezer'?'❄️':'🍱'}</div><div><div class="week-recipe-name">${esc(name)}</div><div class="week-recipe-meta">Uit ${esc(where.toLowerCase())}</div></div></div>`;
  }
  return '';
}
function renderWeekRecipes(){
  const panel=document.querySelector('.food-panel[data-food-panel="recipes"]');
  if(!panel)return;
  let card=document.getElementById('weekRecipesCard');
  if(!card){card=document.createElement('div');card.id='weekRecipesCard';card.className='section-card';panel.prepend(card);}
  const state=readAppState(),plan=Array.isArray(state.weekPlan)?state.weekPlan:[];
  card.innerHTML=`<div class="section-heading"><div><h3 style="margin-bottom:3px;">Deze week</h3><span class="small-muted">De gerechten uit je huidige weekmenu.</span></div></div>${plan.length?`<div class="week-recipes-list">${plan.map(item=>weekRecipeHtml(item,state)).join('')}</div>`:'<div class="week-recipes-empty">Er staat nu geen weekmenu opgeslagen.</div>'}`;
}
function keepWeekRecipesFresh(){
  let queued=false;
  const refresh=()=>{if(queued)return;queued=true;setTimeout(()=>{queued=false;renderWeekRecipes();},60);};
  document.addEventListener('click',e=>{if(e.target.closest?.('[data-food-tab="recipes"],#generateMenuButton,#generateSmartMenuButton,[data-replace-mode],[data-food-day-index]'))refresh();},true);
  const observer=new MutationObserver(m=>{if(m.some(x=>x.target?.id==='recipeLibrary'||x.target?.id==='weekMenu'||x.target?.closest?.('#recipeLibrary,#weekMenu')))refresh();});
  const lib=document.getElementById('recipeLibrary'),menu=document.getElementById('weekMenu');
  if(lib)observer.observe(lib,{childList:true,subtree:true});
  if(menu)observer.observe(menu,{childList:true,subtree:true});
  window.addEventListener('storage',e=>{if(e.key===APP_STATE_KEY)refresh();});
  renderWeekRecipes();
}

function ensureLatestServiceWorker(){
  if(!('serviceWorker'in navigator))return;
  navigator.serviceWorker.getRegistration().then(reg=>{
    if(!reg)return;
    let reloading=false;
    navigator.serviceWorker.addEventListener('controllerchange',()=>{
      if(reloading)return;
      reloading=true;
      location.reload();
    });
    reg.update().catch(()=>{});
  }).catch(()=>{});
}
function boot(){style();readToken();ensure();ensureLatestServiceWorker();keepWeekRecipesFresh();if(valid()){renderConnected();load();}else renderDisconnected();}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});else boot();
})();