const CACHE_NAME='mijn-persoonlijke-app-v1-7-6-widget-calendar-fix';
const APP_SHELL=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./apple-touch-icon.png','./calendar.js','./calendar-widget-bridge.js'];

self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE_NAME).map(key=>caches.delete(key)))));
  self.clients.claim();
});

async function addCalendarScripts(response){
  const type=response.headers.get('content-type')||'';
  if(!type.includes('text/html'))return response;
  let html=await response.text();
  if(!html.includes('calendar-widget-bridge.js'))html=html.replace(/<\/body>/i,'  <script src="./calendar-widget-bridge.js"></script>\n</body>');
  if(!html.includes('calendar.js'))html=html.replace(/<\/body>/i,'  <script src="./calendar.js"></script>\n</body>');
  const headers=new Headers(response.headers);
  headers.delete('content-length');
  return new Response(html,{status:response.status,statusText:response.statusText,headers});
}

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  if(event.request.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const network=await fetch(event.request,{cache:'no-store'});
        return addCalendarScripts(network);
      }catch{
        const cached=await caches.match(event.request)||await caches.match('./index.html');
        return cached?addCalendarScripts(cached):new Response('Offline',{status:503});
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
