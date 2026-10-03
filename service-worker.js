const CACHE_NAME = 'mijn-persoonlijke-app-v1-7-0';
const APP_SHELL = ['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./apple-touch-icon.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE_NAME).then(c=>c.addAll(APP_SHELL)));self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE_NAME).map(k=>caches.delete(k)))));self.clients.claim();});
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;e.respondWith(fetch(e.request).then(r=>{const copy=r.clone();caches.open(CACHE_NAME).then(c=>c.put(e.request,copy));return r;}).catch(()=>caches.match(e.request).then(c=>c||caches.match('./index.html'))));});

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
