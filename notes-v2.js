(() => {
  'use strict';
  if(window.__mijnDagNotesV2Loaded)return;
  window.__mijnDagNotesV2Loaded=true;

  const STORE_KEY = 'mijnPersoonlijkeAppNotesV1';
  const MAIN_KEY = 'mijnPersoonlijkeAppV1';
  const GENERAL = '__general__';
  const ALL = '__all__';

  let data = loadData();
  let activeFolder = ALL;
  let editingId = null;

  function uid(prefix='note') {
    return prefix + '-' + Date.now() + '-' + Math.random().toString(36).slice(2,7);
  }
  function esc(value) {
    return String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  }
  function plainToHtml(text) {
    return esc(String(text || '')).replace(/\n/g,'<br>');
  }
  function stripHtml(html) {
    const d=document.createElement('div'); d.innerHTML=String(html||''); return (d.innerText||d.textContent||'').trim();
  }
  function normalizeFolder(f) {
    return {id:f?.id||uid('folder'),name:String(f?.name||'Map').trim()||'Map',createdAt:Number(f?.createdAt||Date.now())};
  }
  function normalizeNote(n) {
    const fallback=String(n?.text ?? n?.body ?? '');
    const html=typeof n?.html==='string' ? n.html : (typeof n?.bodyHtml==='string' ? n.bodyHtml : plainToHtml(fallback));
    const text=String(n?.plainText ?? n?.text ?? n?.body ?? stripHtml(html));
    return {
      id:n?.id||uid('note'),
      title:String(n?.title||''),
      html,
      plainText:text,
      folderId:n?.folderId||null,
      pinned:Boolean(n?.pinned),
      createdAt:Number(n?.createdAt||Date.now()),
      updatedAt:Number(n?.updatedAt||n?.createdAt||Date.now())
    };
  }
  function loadData() {
    let raw={folders:[],notes:[]};
    try { raw=JSON.parse(localStorage.getItem(STORE_KEY)||'{}')||{}; } catch {}
    const out={
      folders:Array.isArray(raw.folders)?raw.folders.map(normalizeFolder):[],
      notes:Array.isArray(raw.notes)?raw.notes.map(normalizeNote):[]
    };

    // Eenmalige, veilige migratie van de oude eenvoudige notities.
    try {
      const main=JSON.parse(localStorage.getItem(MAIN_KEY)||'{}')||{};
      const old=Array.isArray(main.notes)?main.notes:[];
      const ids=new Set(out.notes.map(n=>n.id));
      old.forEach(n=>{
        const nn=normalizeNote(n);
        if(!ids.has(nn.id) && (nn.plainText||nn.title)){ out.notes.push(nn); ids.add(nn.id); }
      });
    } catch {}

    // Lege test-/placeholdernotities uit oudere versies niet meenemen.
    out.notes=out.notes.filter(n=>(n.title||'').trim()||(n.plainText||'').trim()||stripHtml(n.html||''));
    saveData(out);
    return out;
  }
  function saveData(value=data) {
    try { localStorage.setItem(STORE_KEY,JSON.stringify(value)); } catch {}
  }

  function toast(message) {
    let el=document.getElementById('notesMiniToast');
    if(!el){
      el=document.createElement('div');
      el.id='notesMiniToast';
      el.className='notes-mini-toast';
      document.body.appendChild(el);
    }
    el.textContent=message;
    el.classList.add('show');
    clearTimeout(el._timer);
    el._timer=setTimeout(()=>el.classList.remove('show'),1900);
  }

  function injectStyles() {
    if(document.getElementById('notesV2Styles')) return;
    const style=document.createElement('style');
    style.id='notesV2Styles';
    style.textContent=`
      .notes-v2-shell{padding-bottom:36px}
      .notes-actions{display:grid;grid-template-columns:1fr auto;gap:9px;margin-bottom:12px}
      .notes-folder-strip{display:flex;gap:8px;overflow-x:auto;padding:2px 1px 8px;scrollbar-width:none}
      .notes-folder-strip::-webkit-scrollbar{display:none}
      .notes-folder-chip{white-space:nowrap;border:1px solid var(--line);border-radius:999px;background:#fff;color:var(--muted);padding:9px 13px;font-size:12px;font-weight:850;cursor:pointer}
      .notes-folder-chip.active{background:var(--accent);border-color:var(--accent);color:#fff}
      .notes-folder-tools{display:flex;justify-content:space-between;align-items:center;gap:8px;margin:3px 0 11px}
      .notes-folder-tools span{font-size:12px;color:var(--muted)}
      .notes-folder-tools button{border:0;background:transparent;color:var(--accent-strong);font-weight:850;padding:7px}
      .notes-grid{display:grid;gap:10px}
      .note-v2-card{border:1px solid var(--line);border-radius:19px;background:#fff;padding:13px;box-shadow:0 4px 14px rgba(34,42,34,.04)}
      .note-v2-card.pinned{border-color:rgba(69,98,77,.34);background:linear-gradient(180deg,#fff,#fbfcf8)}
      .note-v2-top{display:grid;grid-template-columns:1fr auto;gap:8px;align-items:start}
      .note-v2-open{border:0;background:transparent;color:var(--text);padding:0;text-align:left;min-width:0;cursor:pointer}
      .note-v2-title{font-size:16px;font-weight:900;line-height:1.3;margin-bottom:5px}
      .note-v2-preview{font-size:14px;line-height:1.5;color:var(--text);max-height:96px;overflow:hidden;word-break:break-word}
      .note-v2-preview ul,.note-v2-preview ol{padding-left:21px;margin:4px 0}
      .note-v2-preview p,.note-v2-preview div{margin:2px 0}
      .note-v2-pin{width:38px;height:38px;border:0;border-radius:12px;background:var(--surface-soft);color:var(--accent-strong);font-size:18px;cursor:pointer}
      .note-v2-meta{display:flex;align-items:center;gap:7px;flex-wrap:wrap;color:var(--muted);font-size:11px;margin-top:9px}
      .note-folder-badge{background:var(--accent-soft);color:var(--accent-strong);border-radius:999px;padding:5px 8px;font-weight:800}
      .note-editor-sheet{max-height:92vh;overflow:auto}
      .note-editor-title{font-size:19px;font-weight:850}
      .note-rich-toolbar{display:flex;gap:7px;overflow-x:auto;padding:8px 0 10px;scrollbar-width:none}
      .note-rich-toolbar::-webkit-scrollbar{display:none}
      .note-format-button{flex:0 0 auto;min-width:42px;height:42px;border:1px solid var(--line);border-radius:12px;background:var(--surface-soft);color:var(--text);font-weight:900;font-size:14px;padding:0 10px}
      .note-color-button{width:38px;min-width:38px;padding:0;border-radius:50%;border:3px solid #fff;box-shadow:0 0 0 1px var(--line)}
      .note-rich-editor{min-height:250px;border:1.5px solid var(--line);border-radius:18px;background:#fff;padding:14px;font-size:16px;line-height:1.55;outline:none;overflow:auto}
      .note-rich-editor:focus{border-color:var(--accent);box-shadow:0 0 0 4px rgba(69,98,77,.10)}
      .note-rich-editor:empty:before{content:'Schrijf je notitie…';color:var(--muted)}
      .note-rich-editor ul,.note-rich-editor ol{padding-left:24px}
      .note-editor-options{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin:12px 0}
      .note-pin-toggle{display:flex;align-items:center;justify-content:space-between;gap:10px;border:1px solid var(--line);border-radius:15px;padding:11px 12px;background:var(--surface-soft);font-weight:800}
      .note-editor-actions{display:grid;grid-template-columns:1fr;gap:8px;margin-top:13px}
      .note-editor-actions.has-delete{grid-template-columns:1fr auto}
      .note-delete-button{border:1px solid #ead7d3;background:#fff7f5;color:var(--danger);border-radius:15px;padding:0 15px;font-weight:850}
      .folder-manage-list{display:grid;gap:8px;margin-top:12px}
      .folder-manage-row{display:grid;grid-template-columns:1fr auto auto;gap:7px;align-items:center;border:1px solid var(--line);border-radius:14px;background:var(--surface-soft);padding:9px 10px}
      .folder-manage-row strong{overflow:hidden;text-overflow:ellipsis}
      .folder-manage-row button{border:0;border-radius:10px;background:#fff;min-height:36px;padding:6px 9px;font-weight:800;color:var(--accent-strong)}
      .folder-manage-row button.danger{color:var(--danger)}
      .folder-create-row{display:grid;grid-template-columns:1fr auto;gap:8px}
      .notes-mini-toast{position:fixed;left:50%;bottom:105px;transform:translate(-50%,14px);z-index:100000;background:#253028;color:#fff;border-radius:999px;padding:10px 14px;font-size:13px;font-weight:800;opacity:0;pointer-events:none;transition:.16s}
      .notes-mini-toast.show{opacity:1;transform:translate(-50%,0)}
    `;
    document.head.appendChild(style);
  }

  function folderName(id) {
    if(!id) return 'Algemeen';
    return data.folders.find(f=>f.id===id)?.name || 'Algemeen';
  }
  function noteTitle(note) {
    const explicit=(note.title||'').trim();
    if(explicit) return explicit;
    const first=(note.plainText||'').split(/\n/).map(x=>x.trim()).find(Boolean);
    return first ? first.slice(0,58) : 'Notitie';
  }
  function formatDate(ts) {
    try { return new Intl.DateTimeFormat('nl-NL',{day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}).format(new Date(ts)); }
    catch { return ''; }
  }

  function replaceNotesView() {
    const section=document.querySelector('.view[data-view="notes"]');
    if(!section || section.dataset.notesV2==='1') return;
    section.dataset.notesV2='1';
    section.innerHTML=`
      <div class="notes-v2-shell">
        <header class="screen-header">
          <button class="back-button" data-back aria-label="Terug naar startscherm">←</button>
          <div class="screen-title-wrap"><h2>Notities</h2><p>Mappen, lijstjes en korte notities op één plek.</p></div>
        </header>

        <div class="section-card">
          <div class="notes-actions">
            <button class="primary-button" id="newRichNoteButton">+ Nieuwe notitie</button>
            <button class="secondary-button" id="openFoldersButton" style="width:auto;padding:0 16px;">Mappen</button>
          </div>
          <div class="notes-folder-strip" id="notesFolderStrip"></div>
          <div class="notes-folder-tools"><span id="notesFolderSummary"></span><button id="quickNewFolderButton">+ Nieuwe map</button></div>
        </div>

        <div class="section-card">
          <div class="section-heading"><div><h3 id="notesListTitle">Alle notities</h3><span class="small-muted">Vastgezette notities eerst</span></div><span class="small-muted" id="noteV2Counter">0</span></div>
          <div class="notes-grid" id="noteV2List"></div>
        </div>
      </div>
    `;

    if(!document.getElementById('noteEditorModal')){
      document.body.insertAdjacentHTML('beforeend',`
        <div class="modal-backdrop" id="noteEditorModal" role="dialog" aria-modal="true" aria-labelledby="noteEditorHeading">
          <div class="sheet note-editor-sheet">
            <div class="sheet-head">
              <div><h3 id="noteEditorHeading">Nieuwe notitie</h3><p class="small-muted" style="margin:5px 0 0;">Eenvoudige opmaak, zonder gedoe.</p></div>
              <button class="close-button" id="closeNoteEditor" aria-label="Sluiten">×</button>
            </div>

            <label class="field-label" for="noteEditorTitle">Titel <span style="font-weight:600;color:var(--muted);">(optioneel)</span></label>
            <input class="text-input note-editor-title" id="noteEditorTitle" placeholder="Titel" />

            <div class="note-editor-options">
              <div>
                <label class="field-label" for="noteEditorFolder">Map</label>
                <select class="text-input" id="noteEditorFolder"></select>
              </div>
              <div>
                <label class="field-label">Vastzetten</label>
                <label class="note-pin-toggle"><span>📌 Bovenaan</span><input type="checkbox" id="noteEditorPinned" /></label>
              </div>
            </div>

            <div class="note-rich-toolbar" aria-label="Tekstopmaak">
              <button class="note-format-button" data-note-command="insertUnorderedList" title="Opsomming">• Lijst</button>
              <button class="note-format-button" data-note-command="insertOrderedList" title="Nummering">1. Lijst</button>
              <button class="note-format-button" id="noteChecklistButton" title="Checklist">☐ Check</button>
              <button class="note-format-button" data-note-command="bold" title="Vet"><b>B</b></button>
              <button class="note-format-button" data-note-command="underline" title="Onderstrepen"><u>U</u></button>
              <button class="note-format-button note-color-button" data-note-color="#45624d" style="background:#45624d" title="Groen"></button>
              <button class="note-format-button note-color-button" data-note-color="#b8892f" style="background:#b8892f" title="Goud"></button>
              <button class="note-format-button note-color-button" data-note-color="#b03737" style="background:#b03737" title="Rood"></button>
              <button class="note-format-button note-color-button" data-note-color="#3f6f9c" style="background:#3f6f9c" title="Blauw"></button>
              <button class="note-format-button" data-note-color="#20251f" title="Normale tekstkleur">A</button>
            </div>

            <div id="noteRichEditor" class="note-rich-editor" contenteditable="true" spellcheck="true"></div>

            <div class="note-editor-actions" id="noteEditorActions">
              <button class="primary-button" id="saveRichNoteButton">Notitie bewaren</button>
              <button class="note-delete-button" id="deleteRichNoteButton" style="display:none;">Verwijderen</button>
            </div>
          </div>
        </div>

        <div class="modal-backdrop" id="noteFoldersModal" role="dialog" aria-modal="true" aria-labelledby="noteFoldersHeading">
          <div class="sheet">
            <div class="sheet-head">
              <div><h3 id="noteFoldersHeading">Mappen</h3><p class="small-muted" style="margin:5px 0 0;">Maak alleen de mappen die je echt gebruikt.</p></div>
              <button class="close-button" id="closeFoldersModal" aria-label="Sluiten">×</button>
            </div>
            <div class="folder-create-row">
              <input class="text-input" id="newFolderName" placeholder="Nieuwe map" />
              <button class="secondary-button" id="createFolderButton" style="width:auto;padding:0 14px;">Toevoegen</button>
            </div>
            <div class="folder-manage-list" id="folderManageList"></div>
          </div>
        </div>
      `);
    }

    const homeNotes=document.querySelector('.home-card.notes-card span');
    if(homeNotes) homeNotes.textContent='Notities en mappen';
  }

  function renderFolderStrip() {
    const strip=document.getElementById('notesFolderStrip');
    if(!strip) return;
    const count=id => id===ALL ? data.notes.length : data.notes.filter(n=>(id===GENERAL?!n.folderId:n.folderId===id)).length;
    const chips=[
      {id:ALL,name:'Alles'},
      {id:GENERAL,name:'Algemeen'},
      ...data.folders.map(f=>({id:f.id,name:f.name}))
    ];
    strip.innerHTML=chips.map(f=>`<button class="notes-folder-chip ${activeFolder===f.id?'active':''}" data-note-folder-filter="${esc(f.id)}">${esc(f.name)} · ${count(f.id)}</button>`).join('');
    const title=document.getElementById('notesListTitle');
    if(title) title.textContent=activeFolder===ALL?'Alle notities':(activeFolder===GENERAL?'Algemeen':folderName(activeFolder));
    const summary=document.getElementById('notesFolderSummary');
    if(summary) summary.textContent=data.folders.length ? data.folders.length+' eigen '+(data.folders.length===1?'map':'mappen') : 'Nog geen eigen mappen';
  }

  function renderNotes() {
    replaceNotesView();
    renderFolderStrip();
    const list=document.getElementById('noteV2List');
    const counter=document.getElementById('noteV2Counter');
    if(!list) return;
    let notes=data.notes.slice();
    if(activeFolder===GENERAL) notes=notes.filter(n=>!n.folderId);
    else if(activeFolder!==ALL) notes=notes.filter(n=>n.folderId===activeFolder);
    notes.sort((a,b)=>(Number(b.pinned)-Number(a.pinned)) || (b.updatedAt-a.updatedAt));
    if(counter) counter.textContent=String(notes.length);
    list.innerHTML=notes.length?notes.map(n=>`
      <div class="note-v2-card ${n.pinned?'pinned':''}">
        <div class="note-v2-top">
          <button class="note-v2-open" data-rich-note-open="${esc(n.id)}">
            <div class="note-v2-title">${esc(noteTitle(n))}</div>
            <div class="note-v2-preview">${n.html||plainToHtml(n.plainText)}</div>
          </button>
          <button class="note-v2-pin" data-rich-note-pin="${esc(n.id)}" aria-label="${n.pinned?'Losmaken':'Vastzetten'}">${n.pinned?'📌':'○'}</button>
        </div>
        <div class="note-v2-meta"><span class="note-folder-badge">${esc(folderName(n.folderId))}</span><span>${esc(formatDate(n.updatedAt))}</span></div>
      </div>
    `).join(''):`<div class="empty-state">Nog geen notities in deze map.</div>`;
  }

  function folderOptions(selected=null) {
    return `<option value="">Algemeen</option>`+data.folders.map(f=>`<option value="${esc(f.id)}" ${selected===f.id?'selected':''}>${esc(f.name)}</option>`).join('');
  }

  function openEditor(id=null) {
    replaceNotesView();
    editingId=id;
    const note=id?data.notes.find(n=>n.id===id):null;
    document.getElementById('noteEditorHeading').textContent=note?'Notitie bewerken':'Nieuwe notitie';
    document.getElementById('noteEditorTitle').value=note?.title||'';
    const select=document.getElementById('noteEditorFolder');
    select.innerHTML=folderOptions(note?.folderId||null);
    select.value=note?.folderId||'';
    document.getElementById('noteEditorPinned').checked=Boolean(note?.pinned);
    document.getElementById('noteRichEditor').innerHTML=note?.html||'';
    document.getElementById('deleteRichNoteButton').style.display=note?'block':'none';
    document.getElementById('noteEditorActions').classList.toggle('has-delete',Boolean(note));
    document.getElementById('noteEditorModal').classList.add('open');
    setTimeout(()=>document.getElementById(note?'noteRichEditor':'noteEditorTitle')?.focus(),80);
  }
  function closeEditor() {
    editingId=null;
    document.getElementById('noteEditorModal')?.classList.remove('open');
  }
  function saveEditor() {
    const title=document.getElementById('noteEditorTitle').value.trim();
    const editor=document.getElementById('noteRichEditor');
    const plain=(editor.innerText||'').trim();
    if(!title&&!plain){toast('Schrijf eerst iets in je notitie.');return;}
    const now=Date.now();
    const existing=editingId?data.notes.find(n=>n.id===editingId):null;
    const note=existing||{id:uid('note'),createdAt:now};
    note.title=title;
    note.html=editor.innerHTML.trim();
    note.plainText=plain;
    note.folderId=document.getElementById('noteEditorFolder').value||null;
    note.pinned=document.getElementById('noteEditorPinned').checked;
    note.updatedAt=now;
    if(!existing)data.notes.push(note);
    saveData(); closeEditor(); renderNotes(); toast(existing?'Notitie bijgewerkt.':'Notitie bewaard.');
  }
  function deleteEditorNote() {
    if(!editingId) return;
    const note=data.notes.find(n=>n.id===editingId);
    if(!note) return;
    if(!confirm('“'+noteTitle(note)+'” verwijderen?')) return;
    data.notes=data.notes.filter(n=>n.id!==editingId);
    saveData(); closeEditor(); renderNotes(); toast('Notitie verwijderd.');
  }

  function openFolders() {
    replaceNotesView();
    renderFolderManager();
    document.getElementById('noteFoldersModal').classList.add('open');
    setTimeout(()=>document.getElementById('newFolderName')?.focus(),60);
  }
  function closeFolders() {
    document.getElementById('noteFoldersModal')?.classList.remove('open');
  }
  function renderFolderManager() {
    const list=document.getElementById('folderManageList'); if(!list)return;
    list.innerHTML=data.folders.length?data.folders.map(f=>`
      <div class="folder-manage-row">
        <strong>${esc(f.name)}</strong>
        <button data-folder-rename="${esc(f.id)}">Naam</button>
        <button class="danger" data-folder-delete="${esc(f.id)}">Wis</button>
      </div>
    `).join(''):`<div class="empty-state">Je hebt nog geen eigen mappen.</div>`;
  }
  function createFolder(name) {
    name=String(name||'').trim();
    if(!name){toast('Geef de map een naam.');return null;}
    if(data.folders.some(f=>f.name.toLowerCase()===name.toLowerCase())){toast('Die map bestaat al.');return null;}
    const folder={id:uid('folder'),name,createdAt:Date.now()};
    data.folders.push(folder);saveData();renderFolderManager();renderFolderStrip();return folder;
  }

  function execCommand(cmd,value=null) {
    const editor=document.getElementById('noteRichEditor');
    if(!editor)return;
    editor.focus();
    try{document.execCommand(cmd,false,value);}catch{}
  }

  function handleEditorEnter(e) {
    if(e.key!=='Enter')return;
    const editor=e.currentTarget,sel=window.getSelection();
    if(!sel||!sel.anchorNode)return;
    let node=sel.anchorNode.nodeType===3?sel.anchorNode.parentElement:sel.anchorNode;
    let block=node?.closest?.('div,p');
    if(!block||!editor.contains(block)) block=editor;
    const text=(block.innerText||block.textContent||'').replace(/\u00a0/g,' ');
    if(/^\s*☐\s/.test(text)){
      e.preventDefault();
      if(/^\s*☐\s*$/.test(text)){
        block.textContent='';
        execCommand('insertText','\n');
      }else{
        execCommand('insertText','\n☐ ');
      }
    }
  }

  document.addEventListener('click',e=>{
    const filter=e.target.closest('[data-note-folder-filter]');
    if(filter){activeFolder=filter.dataset.noteFolderFilter;renderNotes();return;}

    if(e.target.closest('#newRichNoteButton')){openEditor();return;}
    if(e.target.closest('#quickNewFolderButton')){openFolders();return;}
    if(e.target.closest('#openFoldersButton')){openFolders();return;}
    if(e.target.closest('#closeFoldersModal')){closeFolders();return;}
    if(e.target.closest('#closeNoteEditor')){closeEditor();return;}
    if(e.target.closest('#saveRichNoteButton')){saveEditor();return;}
    if(e.target.closest('#deleteRichNoteButton')){deleteEditorNote();return;}

    const open=e.target.closest('[data-rich-note-open]');
    if(open){openEditor(open.dataset.richNoteOpen);return;}
    const pin=e.target.closest('[data-rich-note-pin]');
    if(pin){const n=data.notes.find(x=>x.id===pin.dataset.richNotePin);if(n){n.pinned=!n.pinned;n.updatedAt=Date.now();saveData();renderNotes();}return;}

    const cmd=e.target.closest('[data-note-command]');
    if(cmd){execCommand(cmd.dataset.noteCommand);return;}
    const color=e.target.closest('[data-note-color]');
    if(color){execCommand('foreColor',color.dataset.noteColor);return;}
    if(e.target.closest('#noteChecklistButton')){execCommand('insertText','☐ ');return;}

    if(e.target.closest('#createFolderButton')){
      const inp=document.getElementById('newFolderName');
      const folder=createFolder(inp.value);
      if(folder){inp.value='';toast('Map aangemaakt.');}
      return;
    }
    const rename=e.target.closest('[data-folder-rename]');
    if(rename){
      const f=data.folders.find(x=>x.id===rename.dataset.folderRename);if(!f)return;
      const next=prompt('Nieuwe naam voor de map:',f.name);
      if(next&&next.trim()){f.name=next.trim();saveData();renderFolderManager();renderNotes();toast('Map hernoemd.');}
      return;
    }
    const del=e.target.closest('[data-folder-delete]');
    if(del){
      const f=data.folders.find(x=>x.id===del.dataset.folderDelete);if(!f)return;
      if(!confirm('Map “'+f.name+'” verwijderen? De notities gaan naar Algemeen.'))return;
      data.notes.forEach(n=>{if(n.folderId===f.id)n.folderId=null;});
      data.folders=data.folders.filter(x=>x.id!==f.id);
      if(activeFolder===f.id)activeFolder=GENERAL;
      saveData();renderFolderManager();renderNotes();toast('Map verwijderd.');
      return;
    }
  });

  document.addEventListener('keydown',e=>{
    if(e.target?.id==='newFolderName'&&e.key==='Enter'){e.preventDefault();document.getElementById('createFolderButton')?.click();}
  });

  document.addEventListener('paste',e=>{
    if(e.target?.id!=='noteRichEditor')return;
    e.preventDefault();
    const text=e.clipboardData?.getData('text/plain')||'';
    execCommand('insertText',text);
  });

  document.addEventListener('DOMContentLoaded',()=>{
    injectStyles();
    replaceNotesView();
    renderNotes();
    const editor=document.getElementById('noteRichEditor');
    editor?.addEventListener('keydown',handleEditorEnter);
    const target=new URLSearchParams(location.search).get('open');
    if(target==='note-new')setTimeout(()=>openEditor(),160);
  });

  if(document.readyState!=='loading'){
    injectStyles();replaceNotesView();renderNotes();
    setTimeout(()=>{
      const editor=document.getElementById('noteRichEditor');
      if(editor&&!editor.dataset.enterReady){editor.dataset.enterReady='1';editor.addEventListener('keydown',handleEditorEnter);}
      if(new URLSearchParams(location.search).get('open')==='note-new')openEditor();
    },80);
  }

  window.MijnDagNotes={
    refresh:renderNotes,
    newNote:()=>openEditor(),
    addFromInbox(text){
      text=String(text||'').trim();if(!text)return false;
      const now=Date.now();
      data.notes.push(normalizeNote({id:uid('note'),title:'',body:text,folderId:null,pinned:false,createdAt:now,updatedAt:now}));
      saveData();renderNotes();return true;
    }
  };
})();
