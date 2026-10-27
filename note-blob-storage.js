/* Binaire notitie-bijlagen: gescheiden van de kleine tekst-notities.
 * Geen automatische migratie: bestaande foto's/PDF's in localStorage blijven intact.
 * Back-ups moeten alle gebruikte blob-id's bevatten; een ontbrekend bestand is een fout.
 */
(() => {
  'use strict';
  if (window.MijnDagNoteBlobStorage) return;
  const DB_NAME='mijnDagNoteBlobsV1', TABLE='files', ARCHIVE_VERSION=1;
  let dbPromise;
  function database(){
    if(!('indexedDB' in window))return Promise.reject(new Error('Bestandsopslag niet beschikbaar op dit apparaat.'));
    if(!dbPromise) dbPromise=new Promise((resolve,reject)=>{
      const req=indexedDB.open(DB_NAME,1);
      req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains(TABLE))db.createObjectStore(TABLE,{keyPath:'id'});};
      req.onsuccess=()=>resolve(req.result);
      req.onerror=()=>reject(req.error||new Error('Bestandsopslag openen mislukt.'));
      req.onblocked=()=>reject(new Error('Bestandsopslag wordt door een andere app geblokkeerd.'));
    }).catch(e=>{dbPromise=null;throw e;});
    return dbPromise;
  }
  async function transaction(mode,action){
    const db=await database();
    return new Promise((resolve,reject)=>{
      let result;
      const tx=db.transaction(TABLE,mode);
      tx.oncomplete=()=>resolve(result);
      tx.onabort=()=>reject(tx.error||new Error('Opslaan van bestanden mislukt.'));
      tx.onerror=()=>reject(tx.error||new Error('Bestandsopslag mislukt.'));
      try{action(tx.objectStore(TABLE),r=>{result=r;},()=>tx.abort());}
      catch(err){try{tx.abort();}catch{} reject(err);}
    });
  }
  function put(id,blob){
    if(!id||!(blob instanceof Blob))return Promise.reject(new Error('Ongeldig bestand.'));
    return transaction('readwrite',(table)=>{table.put({id,blob});});
  }
  function get(id){
    return transaction('readonly',(table,set)=>{
      const req=table.get(id);req.onsuccess=()=>set(req.result?.blob||null);
    });
  }
  function remove(id){return transaction('readwrite',table=>{table.delete(id);});}
  function dataUrl(blob){
    return new Promise((resolve,reject)=>{
      const reader=new FileReader();
      reader.onload=()=>resolve(String(reader.result||''));
      reader.onerror=()=>reject(reader.error||new Error('Bijlage lezen mislukt.'));
      reader.readAsDataURL(blob);
    });
  }
  function idbFiles(notes){
    const files=[],ids=new Set();
    for(const note of Array.isArray(notes)?notes:[]){
      for(const file of note.files||[]){
        if(file.storage==='indexeddb'){
          if(!file.id||ids.has(file.id))throw new Error('Ongeldige of dubbele bijlage-ID.');
          ids.add(file.id);files.push(file);
        }
      }
    }
    return files;
  }
  async function exportPayload(notes){
    const refs=idbFiles(notes);
    if(!refs.length)return null;
    const files=[];
    for(const f of refs){
      const blob=await get(f.id);
      if(!blob || blob.size!==f.size)throw new Error('Een bijlage ontbreekt of is beschadigd: '+f.name);
      const src=await dataUrl(blob);
      if(!src.startsWith('data:'+f.mime+';base64,'))throw new Error('Onbekend bijlageformaat: '+f.name);
      files.push({id:f.id,name:f.name,mime:f.mime,size:f.size,src});
    }
    return JSON.stringify({version:ARCHIVE_VERSION,files});
  }
  const DOCX_MIME='application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  const DOC_MIME='application/msword';
  function decode(item){
    const types=['application/pdf',DOCX_MIME,DOC_MIME,'image/jpeg','image/png','image/webp'];
    if(!item || typeof item.id!=='string' || typeof item.src!=='string' ||
       !types.includes(item.mime) || item.size>8*1024*1024 || item.size<1 ||
       !item.src.startsWith('data:'+item.mime+';base64,'))
      throw new Error('Dit back-upbestand bevat een ongeldige bijlage.');
    const binary=atob(item.src.split(',')[1]);
    if(binary.length!==item.size)throw new Error('Een document in de back-up is beschadigd.');
    const pdf=binary.startsWith('%PDF-');
    const jpeg=binary.charCodeAt(0)===0xff&&binary.charCodeAt(1)===0xd8&&binary.charCodeAt(2)===0xff;
    const png=binary.charCodeAt(0)===0x89&&binary.slice(1,4)==='PNG';
    const webp=binary.slice(0,4)==='RIFF'&&binary.slice(8,12)==='WEBP';
    const docx=binary.charCodeAt(0)===0x50&&binary.charCodeAt(1)===0x4b;
    const doc=binary.charCodeAt(0)===0xd0&&binary.charCodeAt(1)===0xcf&&
       binary.charCodeAt(2)===0x11&&binary.charCodeAt(3)===0xe0;
    if(!(item.mime==='application/pdf'&&pdf || item.mime===DOCX_MIME&&docx ||
      item.mime===DOC_MIME&&doc || item.mime==='image/jpeg'&&jpeg ||
      item.mime==='image/png'&&png || item.mime==='image/webp'&&webp))
      throw new Error('Een foto of document in de back-up is beschadigd.');
    const array=new Uint8Array(binary.length);
    for(let i=0;i<binary.length;i++)array[i]=binary.charCodeAt(i);
    return {id:item.id,blob:new Blob([array],{type:item.mime})};
  }
  async function importPayload(value){
    const archive=JSON.parse(value);
    if(archive?.version!==ARCHIVE_VERSION||!Array.isArray(archive.files))throw new Error('Ongeldig bijlagen-archief.');
    if(archive.files.length>100)throw new Error('Te veel bijlagen in het archief.');
    const ids=new Set();
    const decoded=archive.files.map(item=>{
      if(ids.has(item.id))throw new Error('Dubbele bijlage in de back-up.');
      ids.add(item.id);return decode(item);
    });
    await transaction('readwrite',store=>{
      for(const item of decoded)store.put(item);
    });
    return true;
  }
  window.MijnDagNoteBlobStorage={put,get,remove,exportPayload,importPayload};
})();
