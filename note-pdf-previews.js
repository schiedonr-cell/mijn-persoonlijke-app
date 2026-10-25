/* Preview van de eerste pagina van PDF-bijlagen.
 * De PDF-inhoud blijft op het toestel; PDF.js wordt alleen voor zichtbare
 * documenttegels geladen. Zonder renderer blijft de gewone PDF-tegel bestaan.
 * Geen opslagwijzigingen of netwerkverzending van PDF-inhoud.
 */
(() => {
  'use strict';
  if (window.MijnDagPdfPreviews) return;
  const VERSION='5.7.284';
  const LIB='https://cdn.jsdelivr.net/npm/pdfjs-dist@'+VERSION+'/legacy/build/pdf.mjs';
  const WORKER='https://cdn.jsdelivr.net/npm/pdfjs-dist@'+VERSION+'/legacy/build/pdf.worker.mjs';
  const previews=new Map();
  let enginePromise=null, unavailable=false, observer=null, queue=[], running=false;
  let files=new Map(), generation=0;
  function pdfEngine(){
    if(unavailable)return Promise.reject(new Error('PDF-voorbeelden niet beschikbaar'));
    if(!enginePromise)enginePromise=import(LIB).then(pdf=>{
      pdf.GlobalWorkerOptions.workerSrc=WORKER;
      return pdf;
    }).catch(error=>{unavailable=true;enginePromise=null;throw error;});
    return enginePromise;
  }
  async function readBytes(file){
    if(file.storage==='indexeddb'){
      const blob=await window.MijnDagNoteBlobStorage?.get(file.id);
      if(!blob || blob.size!==file.size)throw new Error('Bijlage ontbreekt');
      return new Uint8Array(await blob.arrayBuffer());
    }
    const src=String(file.src||'');
    if(!src.startsWith('data:application/pdf;base64,'))throw new Error('Ongeldig PDF-bestand');
    const encoded=src.slice('data:application/pdf;base64,'.length);
    if(encoded.length>11500000)throw new Error('PDF te groot voor voorbeeld');
    const binary=atob(encoded);
    const bytes=new Uint8Array(binary.length);
    for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
    return bytes;
  }
  async function drawFirstPage(file){
    if(!file || file.mime!=='application/pdf')throw new Error('Niet ondersteund');
    const pdfjs=await pdfEngine();
    const bytes=await readBytes(file);
    if(bytes.length<5 || String.fromCharCode(...bytes.subarray(0,5))!=='%PDF-')
      throw new Error('Ongeldige PDF');
    const loading=pdfjs.getDocument({data:bytes,isEvalSupported:false,disableAutoFetch:true});
    let pdf;
    try{
      pdf=await loading.promise;
      const page=await pdf.getPage(1);
      const original=page.getViewport({scale:1});
      const scale=Math.min(360/original.width,460/original.height,2);
      const viewport=page.getViewport({scale});
      const canvas=document.createElement('canvas');
      canvas.width=Math.max(1,Math.floor(viewport.width));
      canvas.height=Math.max(1,Math.floor(viewport.height));
      const context=canvas.getContext('2d',{alpha:false});
      if(!context)throw new Error('Canvas niet beschikbaar');
      await page.render({canvasContext:context,viewport,background:'#ffffff'}).promise;
      return canvas.toDataURL('image/jpeg',0.78);
    }finally{
      try{await (pdf ? pdf.destroy() : loading.destroy());}catch(_){}
    }
  }
  function remember(id,url){
    previews.delete(id);previews.set(id,url);
    if(previews.size>24)previews.delete(previews.keys().next().value);
  }
  function show(img,url){
    if(!img?.isConnected)return;
    img.src=url;
    img.hidden=false;
    img.closest('.note-v2-cover')?.classList.add('has-preview');
  }
  function enqueue(img,file){
    if(!img?.isConnected || !file)return;
    const existing=previews.get(file.id);
    if(existing){show(img,existing);return;}
    if(!queue.some(item=>item.file.id===file.id))queue.push({img,file,generation});
    void process();
  }
  async function process(){
    if(running)return;
    running=true;
    try{
      while(queue.length){
        const job=queue.shift();
        if(!job || job.generation!==generation || !job.img?.isConnected)continue;
        try{
          const url=previews.get(job.file.id) || await drawFirstPage(job.file);
          if(!previews.has(job.file.id))remember(job.file.id,url);
          if(job.generation===generation)show(job.img,url);
        }catch(_){
          // Geen dataverlies of foutmelding: herkenbare PDF-cover blijft zichtbaar.
          if(unavailable)queue.length=0;
        }
      }
    }finally{running=false;}
  }
  function scan(notes){
    generation++;
    queue=[];
    files=new Map();
    for(const note of notes||[]){
      for(const file of note.files||[]){
        if(file.mime==='application/pdf' && file.id)files.set(file.id,file);
      }
    }
    if(observer){observer.disconnect();observer=null;}
    const targets=[...document.querySelectorAll('img[data-pdf-preview-id]')];
    if(!targets.length || unavailable)return;
    if('IntersectionObserver' in window){
      observer=new IntersectionObserver(entries=>{
        for(const entry of entries){
          if(entry.isIntersecting){
            // Het img-element begint met hidden=true en kan daardoor NOOIT
            // zelf intersecteren. Observeer juist zijn zichtbare documenttegel.
            observer?.unobserve(entry.target);
            const img=entry.target.querySelector('img[data-pdf-preview-id]');
            if(img)enqueue(img,files.get(img.dataset.pdfPreviewId));
          }
        }
      },{rootMargin:'180px 0px'});
    }
    for(const img of targets){
      const file=files.get(img.dataset.pdfPreviewId);
      const existing=file&&previews.get(file.id);
      if(existing){show(img,existing);continue;}
      const cover=img.closest('.note-v2-cover');
      if(observer&&cover)observer.observe(cover);
      else enqueue(img,file);
    }
  }
  window.MijnDagPdfPreviews={scan};
})();
