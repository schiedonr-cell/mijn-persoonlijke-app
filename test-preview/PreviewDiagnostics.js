(function(){
  'use strict';
  function el(id){return document.getElementById(id)}
  function showStatus(){
    const label=el('preview-test-status');
    if(!label)return;
    const bridge=window.AndroidWidgetBridge;
    if(!bridge||typeof bridge.getTestStatus!=='function'){
      label.textContent='Android-koppeling niet actief. Meldingen kunnen niet werken.';
      return;
    }
    try {
      const status=JSON.parse(bridge.getTestStatus());
      const allowed=status.notificationsAllowed===true;
      label.textContent='Versie: '+status.version+' · Android: verbonden · Meldingen: '
        +(allowed?'TOEGESTAAN':'GEBLOKKEERD')
        +' · Laatste alarm: '+(status.lastAlarm||'nog niet ontvangen')
        +(status.focusScheduled?' · Focus-alarm ingepland':'');
      label.style.color=allowed?'#253d2d':'#852c27';
      const settings=el('preview-test-settings');
      if(settings)settings.hidden=allowed;
    } catch(err){label.textContent='Android-koppeling gaf een fout: '+err.message}
  }
  function testAlarm(){
    const bridge=window.AndroidWidgetBridge;
    const message=el('preview-test-message');
    if(!bridge?.scheduleReminder){message.textContent='Geen Android-koppeling beschikbaar.';return;}
    const at=Date.now()+12000;
    try{
      bridge.scheduleReminder('mijndag-diagnose','Testalarm Mijn Dag','Deze melding hoort na 12 seconden te klinken.',at,'focus');
      message.textContent='Testalarm ingepland. Zet nu het scherm uit en wacht 12 seconden. De taak wordt niet afgevinkt.';
      showStatus();
    }catch(error){message.textContent='Alarm inplannen mislukt: '+error.message;}
  }
  function testDirect(){
    const message=el('preview-test-message');
    try{
      window.AndroidWidgetBridge?.testDirectNotification?.();
      message.textContent='Directe testmelding verzonden. Kijk bij je Android-meldingen.';
    }catch(error){message.textContent='Testmelding mislukt: '+error.message}
    showStatus();
  }
  function init(){
    const div=document.createElement('section');
    div.id='preview-test-diagnostics';
    div.style.cssText='background:#eaf2e7;color:#182c20;margin:8px auto 14px;padding:14px;border:2px solid #45624d;border-radius:16px;max-width:520px;font:14px system-ui';
    div.innerHTML='<b>Mijn Dag CONTROLE — meldingen</b>'
      +'<p id="preview-test-status" style="margin:8px 0;font-size:12px">Android-meldingen controleren…</p>'
      +'<div style="display:flex;flex-wrap:wrap;gap:7px">'
      +'<button type="button" id="preview-test-direct" style="min-height:46px;padding:9px;border-radius:9px;border:1px solid #45624d;background:white;font-weight:bold">1. Test melding nu</button>'
      +'<button type="button" id="preview-test-alarm" style="min-height:46px;padding:9px;border-radius:9px;border:1px solid #45624d;background:white;font-weight:bold">2. Test alarm na 12 sec</button>'
      +'<button type="button" id="preview-test-settings" hidden style="min-height:46px;padding:9px;border-radius:9px;border:1px solid #45624d;background:white">Meldingen toestaan</button>'
      +'</div><p id="preview-test-message" style="margin:9px 0 0;font-size:12px">Gebruik eerst knop 1. Pas als die werkt knop 2.</p>';
    const banner=document.querySelector('body > [role="status"]');
    (banner?.parentNode||document.body).insertBefore(div,banner?.nextSibling||document.body.firstChild);
    el('preview-test-direct').addEventListener('click',testDirect);
    el('preview-test-alarm').addEventListener('click',testAlarm);
    el('preview-test-settings').addEventListener('click',()=>{
      window.AndroidWidgetBridge?.openNotificationSettings?.();
    });
    showStatus();
    window.addEventListener('focus',showStatus);
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)showStatus()});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);
  else init();
})();