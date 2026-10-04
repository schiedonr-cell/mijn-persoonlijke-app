(()=>{
'use strict';
const CACHE_KEY='mijnPersoonlijkeAppCalendarEventsV1';
const DATE_KEY=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
const standalone=()=>window.matchMedia?.('(display-mode: standalone)')?.matches||window.navigator.standalone===true;
const esc=v=>String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
function save(items){try{localStorage.setItem(CACHE_KEY,JSON.stringify({date:DATE_KEY(),savedAt:Date.now(),items:Array.isArray(items)?items:[]}));}catch{}}
function read(){try{const x=JSON.parse(localStorage.getItem(CACHE_KEY)||'{}');return x.date===DATE_KEY()&&Array.isArray(x.items)?x:null;}catch{return null;}}
function time(e){if(e?.start?.date&&!e?.start?.dateTime)return'Hele dag';if(!e?.start?.dateTime)return'';const f=new Intl.DateTimeFormat('nl-NL',{hour:'2-digit',minute:'2-digit'}),a=new Date(e.start.dateTime),b=e.end?.dateTime?new Date(e.end.dateTime):null;return b?`${f.format(a)}–${f.format(b)}`:f.format(a);}
function itemsHtml(items){const list=(items||[]).filter(e=>e.status!=='cancelled');if(!list.length)return '<div class="calendar-empty">Geen afspraken voor vandaag.</div>';return list.map(e=>`<div class="calendar-event"><div class="calendar-time">${esc(time(e))}</div><div><div class="calendar-event-title">${esc(e.summary||'Afspraak')}</div>${e.location?`<div class="calendar-event-location">${esc(e.location)}</div>`:''}</div></div>`).join('');}
const originalFetch=window.fetch.bind(window);
window.fetch=async(...args)=>{const r=await originalFetch(...args);try{const u=String(args[0]?.url||args[0]||'');if(r.ok&&u.includes('googleapis.com/calendar/v3/calendars/primary/events')){const data=await r.clone().json();save(data.items||[]);}}catch{}return r;};
function patch(){if(standalone())return;const card=document.getElementById('calendarTodayCard');if(!card)return;const connect=card.querySelector('#calendarConnectButton');if(!connect)return;const cached=read();if(!cached)return;connect.style.display='none';const old=card.querySelector('.calendar-message');if(old)old.remove();let list=card.querySelector('.calendar-list');if(!list){list=document.createElement('div');list.className='calendar-list';card.appendChild(list);}list.innerHTML=itemsHtml(cached.items);let note=card.querySelector('.calendar-widget-cache-note');if(!note){note=document.createElement('div');note.className='calendar-widget-cache-note';note.style.cssText='margin-top:8px;color:var(--muted);font-size:11px;line-height:1.35';card.appendChild(note);}note.textContent=`Agenda laatst bijgewerkt om ${new Intl.DateTimeFormat('nl-NL',{hour:'2-digit',minute:'2-digit'}).format(new Date(cached.savedAt||Date.now()))}.`;
}
document.addEventListener('click',e=>{if(standalone())return;const b=e.target.closest?.('#calendarConnectButton');if(!b)return;const cached=read();if(cached){e.preventDefault();e.stopImmediatePropagation();patch();}},true);
const observer=new MutationObserver(patch);observer.observe(document.documentElement,{childList:true,subtree:true});
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',patch,{once:true});else patch();
})();
