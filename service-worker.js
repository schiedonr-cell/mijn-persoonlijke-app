const CACHE_NAME='mijn-persoonlijke-app-v1-7-9-focus-sound';
const APP_SHELL=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./apple-touch-icon.png','./calendar.js'];

self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(key=>key!==CACHE_NAME).map(key=>caches.delete(key)));
    await self.clients.claim();
    const clients=await self.clients.matchAll({type:'window',includeUncontrolled:true});
    await Promise.all(clients.map(client=>client.navigate(client.url).catch(()=>{})));
  })());
});

function applyDailyFixes(html){
  html=html.replace('<!-- Mijn persoonlijke app v1.7.1 -->','<!-- Mijn persoonlijke app v1.7.9 -->');

  html=html.replace(
    'focus: { taskId: null, targetType: null, targetId: null, durationSec: 300, remainingSec: 300, running: false, soundEnabled: true },',
    'focus: { taskId: null, targetType: null, targetId: null, durationSec: 300, remainingSec: 300, running: false, endAt: null, soundEnabled: true },'
  );

  html=html.replace(
    "focus: {...structuredClone(defaultState.focus), ...(saved.focus||{}), targetType:saved.focus?.targetType||(saved.focus?.taskId?'task':null), targetId:saved.focus?.targetId||saved.focus?.taskId||null, running:false},",
    "focus: {...structuredClone(defaultState.focus), ...(saved.focus||{}), targetType:saved.focus?.targetType||(saved.focus?.taskId?'task':null), targetId:saved.focus?.targetId||saved.focus?.taskId||null, running:Boolean(saved.focus?.running&&Number(saved.focus?.endAt)>Date.now()), endAt:Number(saved.focus?.endAt)||null, remainingSec:(saved.focus?.running&&Number(saved.focus?.endAt)>Date.now())?Math.max(0,Math.ceil((Number(saved.focus.endAt)-Date.now())/1000)):Number(saved.focus?.remainingSec||saved.focus?.durationSec||300)},"
  );

  html=html.replace(
    "function saveState(){ try { const s=structuredClone(state); s.tasks=s.tasks.map(t=>({...t,running:false})); s.focus.running=false; localStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch {} }",
    "function saveState(){ try { const s=structuredClone(state); s.tasks=s.tasks.map(t=>({...t,running:false})); if(!s.focus.running)s.focus.endAt=null; localStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch {} }"
  );

  html=html.replace(
    "function selectFocusItem(type,id){ state.focus.targetType=type;state.focus.targetId=id;state.focus.taskId=type==='task'?id:null;state.focus.remainingSec=state.focus.durationSec||300;state.focus.running=false;saveState();setDailyTab('focus'); }",
    "function selectFocusItem(type,id){ state.focus.targetType=type;state.focus.targetId=id;state.focus.taskId=type==='task'?id:null;state.focus.remainingSec=state.focus.durationSec||300;state.focus.running=false;state.focus.endAt=null;saveState();setDailyTab('focus'); }"
  );

  html=html.replace(
    "const dur=e.target.closest('[data-focus-duration]');if(dur){state.focus.durationSec=Number(dur.dataset.focusDuration)*60;state.focus.remainingSec=state.focus.durationSec;state.focus.running=false;saveState();return renderFocus();}",
    "const dur=e.target.closest('[data-focus-duration]');if(dur){state.focus.durationSec=Number(dur.dataset.focusDuration)*60;state.focus.remainingSec=state.focus.durationSec;state.focus.running=false;state.focus.endAt=null;saveState();return renderFocus();}"
  );

  html=html.replace(
    "if(e.target.closest('#customFocusButton')){const m=Math.max(1,Math.min(180,Number(document.getElementById('customFocusMinutes').value)||0));if(!m)return showToast('Vul een aantal minuten in.');state.focus.durationSec=m*60;state.focus.remainingSec=m*60;state.focus.running=false;saveState();return renderFocus();}",
    "if(e.target.closest('#customFocusButton')){const m=Math.max(1,Math.min(180,Number(document.getElementById('customFocusMinutes').value)||0));if(!m)return showToast('Vul een aantal minuten in.');state.focus.durationSec=m*60;state.focus.remainingSec=m*60;state.focus.running=false;state.focus.endAt=null;saveState();return renderFocus();}"
  );

  html=html.replace(
    "if(e.target.closest('#focusStartButton')){if(!state.focus.running)ensureFocusAudio();state.focus.running=!state.focus.running;if(state.focus.remainingSec<=0)state.focus.remainingSec=state.focus.durationSec||300;saveState();return renderFocus();}",
    "if(e.target.closest('#focusStartButton')){if(!state.focus.running){ensureFocusAudio();if(state.focus.remainingSec<=0)state.focus.remainingSec=state.focus.durationSec||300;state.focus.endAt=Date.now()+state.focus.remainingSec*1000;state.focus.running=true;}else{state.focus.remainingSec=Math.max(0,Math.ceil((Number(state.focus.endAt||Date.now())-Date.now())/1000));state.focus.running=false;state.focus.endAt=null;}saveState();return renderFocus();}"
  );

  html=html.replace(
    "setInterval(()=>{ if(state.focus.running){ state.focus.remainingSec=Math.max(0,(state.focus.remainingSec||0)-1); const task=state.tasks.find(t=>t.id===state.focus.taskId); if(task)task.elapsed=(task.elapsed||0)+1; const el=document.getElementById('focusClockText');if(el)el.textContent=formatTime(state.focus.remainingSec); if(state.focus.remainingSec<=0){state.focus.running=false;playFocusFinishedSignal();showToast('Focusblok klaar.');saveState();renderFocus();} if(state.focus.remainingSec%10===0)saveState(); } },1000);",
    "setInterval(()=>{ if(state.focus.running){ const before=Number(state.focus.remainingSec||0),now=Date.now(); if(!state.focus.endAt)state.focus.endAt=now+before*1000; state.focus.remainingSec=Math.max(0,Math.ceil((Number(state.focus.endAt)-now)/1000)); const elapsed=Math.max(0,before-state.focus.remainingSec),task=state.tasks.find(t=>t.id===state.focus.taskId); if(task&&elapsed)task.elapsed=(task.elapsed||0)+elapsed; const el=document.getElementById('focusClockText');if(el)el.textContent=formatTime(state.focus.remainingSec); if(state.focus.remainingSec<=0){state.focus.running=false;state.focus.endAt=null;playFocusFinishedSignal();showToast('Focusblok klaar.');saveState();renderFocus();} else if(state.focus.remainingSec%10===0)saveState(); } },1000);"
  );

  html=html.replace(
`  function playFocusFinishedSignal(){
    if(!state.focus.soundEnabled) return;
    const ctx=ensureFocusAudio();
    if(ctx){
      try{
        const now=ctx.currentTime;
        [[0,660],[0.18,880],[0.36,1040]].forEach(([delay,freq])=>{
          const osc=ctx.createOscillator(),gain=ctx.createGain();
          osc.type='sine'; osc.frequency.value=freq;
          gain.gain.setValueAtTime(0.0001,now+delay);
          gain.gain.exponentialRampToValueAtTime(0.16,now+delay+0.015);
          gain.gain.exponentialRampToValueAtTime(0.0001,now+delay+0.15);
          osc.connect(gain); gain.connect(ctx.destination);
          osc.start(now+delay); osc.stop(now+delay+0.17);
        });
      }catch{}
    }
    if(navigator.vibrate){ try{ navigator.vibrate([180,100,180,100,320]); }catch{} }
  }`,
`  function playFocusFinishedSignal(){
    if(!state.focus.soundEnabled) return;
    const ctx=ensureFocusAudio();
    if(ctx){
      try{
        const now=ctx.currentTime;
        [[0,880],[0.42,1040],[0.84,880],[1.26,1040],[1.68,880],[2.10,1040],[2.52,880],[2.94,1180]].forEach(([delay,freq])=>{
          const osc=ctx.createOscillator(),gain=ctx.createGain();
          osc.type='sine'; osc.frequency.value=freq;
          gain.gain.setValueAtTime(0.0001,now+delay);
          gain.gain.exponentialRampToValueAtTime(0.32,now+delay+0.02);
          gain.gain.exponentialRampToValueAtTime(0.0001,now+delay+0.28);
          osc.connect(gain); gain.connect(ctx.destination);
          osc.start(now+delay); osc.stop(now+delay+0.31);
        });
      }catch{}
    }
    if(navigator.vibrate){ try{ navigator.vibrate([220,180,220,180,220,180,220,180,220,180,220,180,220,180,350]); }catch{} }
  }`
  );

  html=html.replace(
    "function renderShopping(){ const wrap=document.getElementById('shoppingContent'); if(!wrap)return; if(!state.shopping.length)",
    "function renderShopping(){ const wrap=document.getElementById('shoppingContent'); if(!wrap)return; const hadShoppingGroups=Boolean(wrap.querySelector('.shop-group')); const openShoppingGroups=new Set([...wrap.querySelectorAll('.shop-group[open]')].map(d=>d.querySelector('summary span')?.textContent||'')); if(!state.shopping.length)"
  );

  html=html.replace(
    "<details class=\"shop-group\" ${gi<2?'open':''}>",
    "<details class=\"shop-group\" ${openShoppingGroups.has(cat)||(!hadShoppingGroups&&gi<2)?'open':''}>"
  );

  html=html.replace(
    '<button class="focus-list-button" data-focus-household="${t.id}">Focus</button></div>',
    '<div class="focus-item-actions"><button class="focus-list-button" data-focus-household="${t.id}">Focus</button>${t.fixedReminder?\'\':`<button class="focus-list-button" data-household-tomorrow="${t.id}">Morgen</button><button class="focus-list-button" data-household-swap="${t.id}">Andere</button>`}</div></div>'
  );

  html=html.replace(
    "const htom=e.target.closest('[data-household-tomorrow]');if(htom){const t=state.householdTasks.find(x=>x.id===htom.dataset.householdTomorrow);if(t){t.deferUntil=addDaysKey(1);t.paused=false;saveState();renderHousehold();renderHouseholdManage();showToast('Huishoudklus staat voor morgen.');}return;}",
    "const htom=e.target.closest('[data-household-tomorrow]');if(htom){const t=state.householdTasks.find(x=>x.id===htom.dataset.householdTomorrow);if(t){t.deferUntil=addDaysKey(1);t.paused=false;saveState();renderHousehold();renderHouseholdManage();showToast('Huishoudklus staat voor morgen.');}return;} const hswap=e.target.closest('[data-household-swap]');if(hswap){const t=state.householdTasks.find(x=>x.id===hswap.dataset.householdSwap);if(t){t.deferUntil=addDaysKey(1);t.paused=false;saveState();renderHousehold();renderHouseholdManage();showToast('Andere huishoudtaak gekozen. Deze komt morgen terug.');}return;}"
  );

  return html;
}

async function enhanceHtml(response){
  const type=response.headers.get('content-type')||'';
  if(!type.includes('text/html'))return response;
  let html=applyDailyFixes(await response.text());
  if(!html.includes('calendar.js'))html=html.replace(/<\/body>/i,'  <script src="./calendar.js"></script>\n</body>');
  const headers=new Headers(response.headers);
  headers.delete('content-length');
  headers.set('content-type','text/html; charset=utf-8');
  return new Response(html,{status:response.status,statusText:response.statusText,headers});
}

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  if(event.request.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const network=await fetch(event.request,{cache:'no-store'});
        return enhanceHtml(network);
      }catch{
        const cached=await caches.match(event.request)||await caches.match('./index.html');
        return cached?enhanceHtml(cached):new Response('Offline',{status:503});
      }
    })());
    return;
  }
  event.respondWith(fetch(event.request).then(r=>{const copy=r.clone();caches.open(CACHE_NAME).then(c=>c.put(event.request,copy)).catch(()=>{});return r;}).catch(()=>caches.match(event.request)));
});

self.addEventListener('push',event=>{
  let data={};
  try{data=event.data?event.data.json():{};}catch{try{data={body:event.data?.text()||''};}catch{data={};}}
  const title=data.title||'Mijn app';
  const options={body:data.body||'Je hebt nog iets kleins openstaan.',tag:data.tag||'mijn-app-herinnering',icon:'./icon-192.png',badge:'./icon-192.png',data:{url:data.url||'./'},renotify:false};
  event.waitUntil(self.registration.showNotification(title,options));
});

self.addEventListener('notificationclick',event=>{
  event.notification.close();
  const target=event.notification?.data?.url||'./';
  event.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(list=>{
    for(const client of list){if('focus'in client&&client.url.includes('/mijn-persoonlijke-app/')){client.navigate(target).catch(()=>{});return client.focus();}}
    return self.clients.openWindow?self.clients.openWindow(target):undefined;
  }));
});
