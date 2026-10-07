(()=>{
'use strict';
if(window.__mijnDagNotesLoaded)return;
window.__mijnDagNotesLoaded=true;

const NOTES_KEY='mijnPersoonlijkeAppNotesV1';
const WIDGET_SESSION_KEY='mijnPersoonlijkeAppNotesWidgetHandledV1';
let data=loadData();
let selectedFolder='all';
let searchTerm='';
let editingNoteId=null;
let editingFolderId=null;
let editorSnapshot='';

const esc=value=>String(value??'').replace(/[&<>\'\"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
const uid=prefix=>`${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,8)}`;
const now=()=>Date.now();

function loadData(){
  try{
    const raw=JSON.parse(localStorage.getItem(NOTES_KEY)||'{}');
    return {
      folders:Array.isArray(raw.folders)?raw.folders.filter(x=>x&&x.id&&x.name).map(x=>({id:String(x.id),name:String(x.name),createdAt:Number(x.createdAt)||now(),updatedAt:Number(x.updatedAt)||Number(x.createdAt)||now()})):[],
      notes:Array.isArray(raw.notes)?raw.notes.filter(x=>x&&x.id).map(x=>({id:String(x.id),title:String(x.title||''),body:String(x.body||''),folderId:x.folderId?String(x.folderId):null,pinned:Boolean(x.pinned),createdAt:Number(x.createdAt)||now(),updatedAt:Number(x.updatedAt)||Number(x.createdAt)||now()})):[]
    };
  }catch{return {folders:[],notes:[]};}
}
function saveData(){try{localStorage.setItem(NOTES_KEY,JSON.stringify(data));}catch{}}
function folderById(id){return data.folders.find(folder=>folder.id===id)||null;}
function noteById(id){return data.notes.find(note=>note.id===id)||null;}
function noteCount(folderId){return folderId==='all'?data.notes.length:folderId==='none'?data.notes.filter(n=>!n.folderId).length:data.notes.filter(n=>n.folderId===folderId).length;}
function currentFolderLabel(){if(selectedFolder==='all')return'Alle notities';if(selectedFolder==='none')return'Zonder map';return folderById(selectedFolder)?.name||'Alle notities';}
function normalized(text){return String(text||'').trim().toLocaleLowerCase('nl-NL');}
function noteTitle(note){const title=String(note.title||'').trim();if(title)return title;const first=String(note.body||'').split(/\n/).map(x=>x.trim()).find(Boolean);return first?first.slice(0,80):'Naamloze notitie';}
function notePreview(note){const title=String(note.title||'').trim(),body=String(note.body||'').trim().replace(/\s+/g,' ');if(body)return body.slice(0,145);return title?'Geen extra tekst.':'Tik om te schrijven.';}
function formatUpdated(ms){
  const d=new Date(Number(ms)||Date.now()),today=new Date(),yesterday=new Date();yesterday.setDate(today.getDate()-1);
  const same=(a,b)=>a.getFullYear()===b.getFullYear()&&a.getMonth()===b.getMonth()&&a.getDate()===b.getDate();
  const time=new Intl.DateTimeFormat('nl-NL',{hour:'2-digit',minute:'2-digit'}).format(d);
  if(same(d,today))return`Vandaag · ${time}`;
  if(same(d,yesterday))return`Gisteren · ${time}`;
  return new Intl.DateTimeFormat('nl-NL',{day:'numeric',month:'short',year:d.getFullYear()===today.getFullYear()?undefined:'numeric'}).format(d);
}
function toast(message){
  const el=document.getElementById('toast');
  if(!el)return;
  el.textContent=message;el.classList.add('show');
  clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.classList.remove('show'),2100);
}

function installStyles(){
  if(document.getElementById('notesIntegrationStyles'))return;
  const style=document.createElement('style');style.id='notesIntegrationStyles';style.textContent=`
  .notes-shell{padding-bottom:30px}.notes-top-actions{display:grid;gap:10px;margin-bottom:14px}.notes-new-button{min-height:60px;font-size:17px}.notes-search-wrap{position:relative}.notes-search{width:100%;min-height:52px;border:1.5px solid var(--line);border-radius:16px;background:var(--surface);padding:12px 44px 12px 15px;color:var(--text);font:inherit;outline:none}.notes-search:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}.notes-search-icon{position:absolute;right:15px;top:50%;transform:translateY(-50%);color:var(--muted);pointer-events:none;font-size:18px}.notes-folder-card{padding:16px}.notes-folder-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:11px}.notes-folder-head h3{margin:0}.notes-small-button{min-height:40px;border:0;border-radius:13px;background:var(--accent-soft);color:var(--accent-strong);font-weight:850;padding:8px 12px;cursor:pointer}.notes-folders{display:grid;grid-template-columns:1fr 1fr;gap:8px}.notes-folder{min-height:62px;border:1px solid var(--line);border-radius:16px;background:var(--surface-soft);color:var(--text);padding:10px 12px;text-align:left;cursor:pointer;display:flex;align-items:center;gap:10px}.notes-folder.active{background:var(--accent);border-color:var(--accent);color:#fff}.notes-folder-icon{font-size:20px;flex:0 0 auto}.notes-folder-main{min-width:0;flex:1}.notes-folder-name{font-weight:850;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.notes-folder-count{font-size:11px;color:var(--muted);margin-top:2px}.notes-folder.active .notes-folder-count{color:rgba(255,255,255,.78)}.notes-selected-folder-actions{display:flex;gap:8px;margin-top:10px}.notes-selected-folder-actions button{flex:1;min-height:42px;border:1px solid var(--line);border-radius:13px;background:#fff;color:var(--text);font-weight:800;cursor:pointer}.notes-selected-folder-actions button.danger{color:var(--danger)}.notes-list-card{padding:16px}.notes-list-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10px;margin-bottom:12px}.notes-list-head h3{margin:0}.notes-list{display:grid;gap:9px}.note-card{width:100%;border:1px solid var(--line);border-radius:18px;background:#fff;padding:13px 14px;text-align:left;color:var(--text);cursor:pointer;box-shadow:0 3px 10px rgba(34,42,34,.03)}.note-card:active{transform:scale(.992)}.note-card-top{display:flex;align-items:flex-start;gap:10px}.note-card-title{font-weight:900;line-height:1.3;flex:1;min-width:0}.note-pin{color:#6d5538;font-size:16px}.note-card-preview{font-size:13px;line-height:1.45;color:var(--muted);margin-top:6px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.note-card-meta{display:flex;align-items:center;gap:7px;flex-wrap:wrap;margin-top:9px;font-size:11px;color:var(--muted)}.note-folder-badge{display:inline-flex;align-items:center;min-height:24px;padding:4px 8px;border-radius:999px;background:var(--surface-soft);font-weight:750}.notes-empty{text-align:center;padding:24px 10px 14px;color:var(--muted);line-height:1.5}.notes-empty-icon{font-size:34px;margin-bottom:8px}.notes-modal{position:fixed;inset:0;z-index:10010;background:rgba(25,31,26,.55);display:none;align-items:flex-end;justify-content:center;padding:18px 12px max(18px,env(safe-area-inset-bottom));backdrop-filter:blur(4px)}.notes-modal.open{display:flex}.notes-sheet{width:min(100%,520px);max-height:min(92vh,820px);overflow:auto;background:var(--surface);border-radius:26px;padding:18px;box-shadow:0 24px 70px rgba(0,0,0,.24)}.notes-sheet-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}.notes-sheet-head h3{margin:0;font-size:22px}.notes-close{width:44px;height:44px;border:1px solid var(--line);border-radius:14px;background:var(--surface-soft);font-size:24px;color:var(--text);cursor:pointer}.notes-field{display:grid;gap:6px;margin-bottom:11px}.notes-field label{font-size:12px;color:var(--muted);font-weight:800;margin-left:2px}.notes-input,.notes-textarea,.notes-select{width:100%;border:1.5px solid var(--line);border-radius:15px;background:#fff;color:var(--text);padding:12px 13px;font:inherit;outline:none}.notes-input,.notes-select{min-height:50px}.notes-textarea{min-height:230px;resize:vertical;line-height:1.5}.notes-input:focus,.notes-textarea:focus,.notes-select:focus{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}.notes-editor-actions{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:10px}.notes-editor-actions .primary-button,.notes-editor-actions .secondary-button{min-height:52px}.notes-pin-button{width:100%;min-height:48px;border:1px solid var(--line);border-radius:15px;background:var(--surface-soft);color:var(--text);font-weight:850;cursor:pointer;margin-bottom:4px}.notes-pin-button.active{background:var(--warm);color:#6d5538;border-color:#dbc8aa}.notes-delete{grid-column:1/-1;color:var(--danger)!important}.notes-action-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:12px}.notes-action-grid button{min-height:52px;border:1px solid var(--line);border-radius:14px;background:var(--surface-soft);color:var(--text);font-weight:850;cursor:pointer;padding:8px 10px}.notes-action-grid button.primary{background:var(--accent);color:#fff;border-color:var(--accent)}.notes-action-grid button.accent{background:var(--accent-soft);color:var(--accent-strong);border-color:transparent}.notes-action-grid button.danger{grid-column:1/-1;color:var(--danger);background:#fff7f5;border-color:#ead7d3}.notes-action-move{margin-top:12px}.notes-action-move label{display:block;font-weight:800;font-size:13px;margin-bottom:6px}.notes-folder-sheet .notes-input{margin-bottom:12px}.notes-folder-sheet-actions{display:grid;grid-template-columns:1fr 1fr;gap:9px}.notes-folder-sheet-actions button{min-height:50px}.notes-hint{font-size:12px;color:var(--muted);line-height:1.45;margin:-2px 2px 12px}.home-card.notes-card span{line-height:1.35}@media(max-width:380px){.notes-folders{grid-template-columns:1fr}.notes-editor-actions{grid-template-columns:1fr}.notes-delete{grid-column:auto}.notes-textarea{min-height:200px}}
  `;document.head.appendChild(style);
}

function installView(){
  const view=document.querySelector('section.view[data-view="notes"]');
  if(!view)return null;
  view.classList.add('notes-shell');
  view.innerHTML=`
    <header class="screen-header">
      <button class="back-button" data-back aria-label="Terug naar startscherm">←</button>
      <div class="screen-title-wrap"><h2>Notities</h2><p>Snel opschrijven en terugvinden.</p></div>
    </header>
    <div class="notes-top-actions">
      <button class="primary-button notes-new-button" id="newNoteButton" type="button">+ Nieuwe notitie</button>
      <div class="notes-search-wrap"><input class="notes-search" id="notesSearch" type="search" placeholder="Zoek in notities" autocomplete="off" aria-label="Zoek in notities"><span class="notes-search-icon">⌕</span></div>
    </div>
    <div class="section-card notes-folder-card">
      <div class="notes-folder-head"><h3>Mappen</h3><button class="notes-small-button" id="newFolderButton" type="button">+ Nieuwe map</button></div>
      <div class="notes-folders" id="notesFolders"></div>
      <div class="notes-selected-folder-actions" id="notesFolderActions" hidden></div>
    </div>
    <div class="section-card notes-list-card">
      <div class="notes-list-head"><div><h3 id="notesListTitle">Alle notities</h3><div class="small-muted" id="notesListCount"></div></div></div>
      <div class="notes-list" id="notesList"></div>
    </div>
    <div class="notes-modal" id="noteActionModal" role="dialog" aria-modal="true" aria-labelledby="noteActionTitle">
      <div class="notes-sheet">
        <div class="notes-sheet-head"><div><h3 id="noteActionTitle">Notitie</h3><div class="small-muted" id="noteActionMeta"></div></div><button class="notes-close" id="closeNoteAction" type="button" aria-label="Sluiten">×</button></div>
        <div class="notes-action-move"><label for="noteActionFolder">Verplaatsen naar map</label><select class="notes-select" id="noteActionFolder"></select></div>
        <div class="notes-action-grid">
          <button class="primary" id="noteActionEdit" type="button">Bewerken</button>
          <button class="accent" id="noteActionPin" type="button">☆ Vastzetten</button>
          <button id="noteActionMove" type="button">Verplaatsen</button>
          <button id="noteActionOpen" type="button">Open notitie</button>
          <button class="danger" id="noteActionDelete" type="button">Verwijderen</button>
        </div>
      </div>
    </div>
    <div class="notes-modal" id="noteEditorModal" role="dialog" aria-modal="true" aria-labelledby="noteEditorTitle">
      <div class="notes-sheet">
        <div class="notes-sheet-head"><div><h3 id="noteEditorTitle">Nieuwe notitie</h3><div class="small-muted" id="noteEditorStatus">Nog niet opgeslagen</div></div><button class="notes-close" id="closeNoteEditor" type="button" aria-label="Sluiten">×</button></div>
        <div class="notes-field"><label for="noteTitleInput">Titel</label><input class="notes-input" id="noteTitleInput" type="text" maxlength="160" placeholder="Titel (optioneel)" autocomplete="off"></div>
        <div class="notes-field"><label for="noteBodyInput">Notitie</label><textarea class="notes-textarea" id="noteBodyInput" placeholder="Schrijf hier..."></textarea></div>
        <div class="notes-field"><label for="noteFolderSelect">Map</label><select class="notes-select" id="noteFolderSelect"></select></div>
        <button class="notes-pin-button" id="notePinButton" type="button">☆ Vastzetten</button>
        <div class="notes-editor-actions"><button class="secondary-button" id="cancelNoteButton" type="button">Sluiten</button><button class="primary-button" id="saveNoteButton" type="button">Bewaren</button><button class="secondary-button notes-delete" id="deleteNoteButton" type="button" hidden>Notitie verwijderen</button></div>
      </div>
    </div>
    <div class="notes-modal" id="folderEditorModal" role="dialog" aria-modal="true" aria-labelledby="folderEditorTitle">
      <div class="notes-sheet notes-folder-sheet">
        <div class="notes-sheet-head"><div><h3 id="folderEditorTitle">Nieuwe map</h3><div class="small-muted">Geef de map een korte, duidelijke naam.</div></div><button class="notes-close" id="closeFolderEditor" type="button" aria-label="Sluiten">×</button></div>
        <input class="notes-input" id="folderNameInput" type="text" maxlength="60" placeholder="Naam van de map" autocomplete="off">
        <div class="notes-folder-sheet-actions"><button class="secondary-button" id="cancelFolderButton" type="button">Annuleren</button><button class="primary-button" id="saveFolderButton" type="button">Bewaren</button></div>
      </div>
    </div>`;
  const subtitle=document.querySelector('.home-card.notes-card span');if(subtitle)subtitle.textContent='Notities en mappen';
  return view;
}

function renderFolders(){
  const wrap=document.getElementById('notesFolders');if(!wrap)return;
  const folderButtons=[
    {id:'all',name:'Alle notities',icon:'🗒️'},
    {id:'none',name:'Zonder map',icon:'📄'},
    ...data.folders.slice().sort((a,b)=>a.name.localeCompare(b.name,'nl')).map(folder=>({id:folder.id,name:folder.name,icon:'📁'}))
  ];
  wrap.innerHTML=folderButtons.map(folder=>`<button type="button" class="notes-folder ${selectedFolder===folder.id?'active':''}" data-notes-folder="${esc(folder.id)}"><span class="notes-folder-icon">${folder.icon}</span><span class="notes-folder-main"><span class="notes-folder-name">${esc(folder.name)}</span><span class="notes-folder-count">${noteCount(folder.id)} ${noteCount(folder.id)===1?'notitie':'notities'}</span></span></button>`).join('');
  const actions=document.getElementById('notesFolderActions');
  const custom=folderById(selectedFolder);
  if(custom){actions.hidden=false;actions.innerHTML=`<button type="button" data-folder-rename="${esc(custom.id)}">Hernoemen</button><button type="button" class="danger" data-folder-delete="${esc(custom.id)}">Map verwijderen</button>`;}
  else{actions.hidden=true;actions.innerHTML='';}
}

function visibleNotes(){
  const q=normalized(searchTerm);
  return data.notes.filter(note=>{
    if(selectedFolder==='none'&&note.folderId)return false;
    if(selectedFolder!=='all'&&selectedFolder!=='none'&&note.folderId!==selectedFolder)return false;
    if(!q)return true;
    const folder=folderById(note.folderId)?.name||'';
    return normalized(`${note.title} ${note.body} ${folder}`).includes(q);
  }).sort((a,b)=>Number(b.pinned)-Number(a.pinned)||Number(b.updatedAt)-Number(a.updatedAt));
}
function renderNotes(){
  const list=document.getElementById('notesList'),title=document.getElementById('notesListTitle'),count=document.getElementById('notesListCount');if(!list||!title||!count)return;
  const notes=visibleNotes();title.textContent=currentFolderLabel();count.textContent=searchTerm?`${notes.length} gevonden`:`${notes.length} ${notes.length===1?'notitie':'notities'}`;
  if(!notes.length){
    const text=searchTerm?'Geen notities gevonden.':selectedFolder==='all'?'Nog geen notities. Tik op “Nieuwe notitie” om te beginnen.':'In deze map staan nog geen notities.';
    list.innerHTML=`<div class="notes-empty"><div class="notes-empty-icon">✎</div>${esc(text)}</div>`;return;
  }
  list.innerHTML=notes.map(note=>{const folder=folderById(note.folderId);return `<button type="button" class="note-card" data-note-open="${esc(note.id)}"><div class="note-card-top"><div class="note-card-title">${esc(noteTitle(note))}</div>${note.pinned?'<span class="note-pin" aria-label="Vastgezet">★</span>':''}</div><div class="note-card-preview">${esc(notePreview(note))}</div><div class="note-card-meta">${folder?`<span class="note-folder-badge">📁 ${esc(folder.name)}</span>`:'<span class="note-folder-badge">Zonder map</span>'}<span>${esc(formatUpdated(note.updatedAt))}</span></div></button>`;}).join('');
}
function renderAll(){renderFolders();renderNotes();}
function addFromInbox(text){
  const body=String(text||'').trim();if(!body)return false;
  const stamp=now();
  data.notes.unshift({id:uid('note'),title:'',body,folderId:null,pinned:false,createdAt:stamp,updatedAt:stamp});
  selectedFolder='all';searchTerm='';
  const search=document.getElementById('notesSearch');if(search)search.value='';
  saveData();renderAll();return true;
}
window.MijnDagNotes={addFromInbox,refresh:()=>{data=loadData();renderAll();}};


function populateFolderSelect(value){
  const select=document.getElementById('noteFolderSelect');if(!select)return;
  select.innerHTML=`<option value="">Zonder map</option>${data.folders.slice().sort((a,b)=>a.name.localeCompare(b.name,'nl')).map(folder=>`<option value="${esc(folder.id)}">${esc(folder.name)}</option>`).join('')}`;
  select.value=value&&folderById(value)?value:'';
}
function editorState(){return JSON.stringify({title:document.getElementById('noteTitleInput')?.value||'',body:document.getElementById('noteBodyInput')?.value||'',folderId:document.getElementById('noteFolderSelect')?.value||'',pinned:document.getElementById('notePinButton')?.classList.contains('active')||false});}
function setPinButton(on){const button=document.getElementById('notePinButton');if(!button)return;button.classList.toggle('active',Boolean(on));button.textContent=on?'★ Vastgezet':'☆ Vastzetten';}
function openNoteAction(id){
  const note=noteById(id);if(!note)return;
  actionNoteId=id;
  document.getElementById('noteActionTitle').textContent=noteTitle(note);
  document.getElementById('noteActionMeta').textContent=formatUpdated(note.updatedAt);
  const select=document.getElementById('noteActionFolder');
  select.innerHTML=`<option value="">Zonder map</option>${data.folders.slice().sort((a,b)=>a.name.localeCompare(b.name,'nl')).map(folder=>`<option value="${esc(folder.id)}">${esc(folder.name)}</option>`).join('')}`;
  select.value=note.folderId&&folderById(note.folderId)?note.folderId:'';
  document.getElementById('noteActionPin').textContent=note.pinned?'★ Niet meer vastzetten':'☆ Vastzetten';
  document.getElementById('noteActionModal').classList.add('open');
  document.body.style.overflow='hidden';
}
function closeNoteAction(){
  document.getElementById('noteActionModal')?.classList.remove('open');actionNoteId=null;document.body.style.overflow='';
}
function moveActionNote(){
  const note=noteById(actionNoteId);if(!note)return;
  const value=document.getElementById('noteActionFolder').value||null;
  note.folderId=value&&folderById(value)?value:null;note.updatedAt=now();saveData();renderAll();closeNoteAction();toast('Notitie verplaatst.');
}
function toggleActionNotePin(){
  const note=noteById(actionNoteId);if(!note)return;
  note.pinned=!note.pinned;note.updatedAt=now();saveData();renderAll();document.getElementById('noteActionPin').textContent=note.pinned?'★ Niet meer vastzetten':'☆ Vastzetten';toast(note.pinned?'Notitie vastgezet.':'Notitie niet meer vastgezet.');
}
function deleteActionNote(){
  const note=noteById(actionNoteId);if(!note)return;
  if(!confirm(`“${noteTitle(note)}” verwijderen?`))return;
  data.notes=data.notes.filter(n=>n.id!==note.id);saveData();renderAll();closeNoteAction();toast('Notitie verwijderd.');
}

function openNoteEditor(id=null){
  editingNoteId=id||null;const note=id?noteById(id):null;
  document.getElementById('noteEditorTitle').textContent=note?'Notitie bewerken':'Nieuwe notitie';
  document.getElementById('noteEditorStatus').textContent=note?`Laatst aangepast: ${formatUpdated(note.updatedAt)}`:'Nog niet opgeslagen';
  document.getElementById('noteTitleInput').value=note?.title||'';
  document.getElementById('noteBodyInput').value=note?.body||'';
  const defaultFolder=note?.folderId||(selectedFolder!=='all'&&selectedFolder!=='none'&&folderById(selectedFolder)?selectedFolder:null);
  populateFolderSelect(defaultFolder);
  setPinButton(Boolean(note?.pinned));
  document.getElementById('deleteNoteButton').hidden=!note;
  document.getElementById('noteEditorModal').classList.add('open');
  document.body.style.overflow='hidden';
  editorSnapshot=editorState();
  setTimeout(()=>document.getElementById(note?'noteTitleInput':'noteBodyInput')?.focus(),80);
}
function hasEditorChanges(){return editorState()!==editorSnapshot;}
function closeNoteEditor(force=false){
  const modal=document.getElementById('noteEditorModal');if(!modal?.classList.contains('open'))return true;
  if(!force&&hasEditorChanges()&&!confirm('Je hebt wijzigingen die nog niet zijn bewaard. Sluiten zonder bewaren?'))return false;
  modal.classList.remove('open');editingNoteId=null;editorSnapshot='';document.body.style.overflow='';return true;
}
function saveNote(){
  const title=document.getElementById('noteTitleInput').value.trim(),body=document.getElementById('noteBodyInput').value.trim(),folderId=document.getElementById('noteFolderSelect').value||null,pinned=document.getElementById('notePinButton').classList.contains('active');
  if(!title&&!body){toast('Schrijf eerst iets in de notitie.');document.getElementById('noteBodyInput').focus();return;}
  const stamp=now();
  if(editingNoteId){const note=noteById(editingNoteId);if(!note)return;note.title=title;note.body=body;note.folderId=folderById(folderId)?folderId:null;note.pinned=pinned;note.updatedAt=stamp;}
  else{const note={id:uid('note'),title,body,folderId:folderById(folderId)?folderId:null,pinned,createdAt:stamp,updatedAt:stamp};data.notes.unshift(note);editingNoteId=note.id;}
  saveData();renderAll();editorSnapshot=editorState();closeNoteEditor(true);toast('Notitie bewaard.');
}
function deleteNote(){
  const note=noteById(editingNoteId);if(!note)return;
  if(!confirm(`“${noteTitle(note)}” verwijderen?`))return;
  data.notes=data.notes.filter(n=>n.id!==note.id);saveData();renderAll();closeNoteEditor(true);toast('Notitie verwijderd.');
}

function openFolderEditor(id=null){
  editingFolderId=id||null;const folder=id?folderById(id):null;
  document.getElementById('folderEditorTitle').textContent=folder?'Map hernoemen':'Nieuwe map';
  document.getElementById('folderNameInput').value=folder?.name||'';
  document.getElementById('folderEditorModal').classList.add('open');document.body.style.overflow='hidden';
  setTimeout(()=>document.getElementById('folderNameInput')?.focus(),80);
}
function closeFolderEditor(){document.getElementById('folderEditorModal')?.classList.remove('open');editingFolderId=null;document.body.style.overflow='';}
function saveFolder(){
  const name=document.getElementById('folderNameInput').value.trim();if(!name){toast('Geef de map eerst een naam.');return;}
  const duplicate=data.folders.some(folder=>normalized(folder.name)===normalized(name)&&folder.id!==editingFolderId);if(duplicate){toast('Er bestaat al een map met deze naam.');return;}
  const stamp=now();
  if(editingFolderId){const folder=folderById(editingFolderId);if(!folder)return;folder.name=name;folder.updatedAt=stamp;}
  else{const folder={id:uid('folder'),name,createdAt:stamp,updatedAt:stamp};data.folders.push(folder);selectedFolder=folder.id;}
  saveData();renderAll();closeFolderEditor();toast('Map bewaard.');
}
function deleteFolder(id){
  const folder=folderById(id);if(!folder)return;
  const count=data.notes.filter(note=>note.folderId===id).length;
  const extra=count?` De ${count===1?'notitie':'notities'} in deze map ${count===1?'blijft':'blijven'} bewaard onder “Zonder map”.`:'';
  if(!confirm(`Map “${folder.name}” verwijderen?${extra}`))return;
  data.notes.forEach(note=>{if(note.folderId===id){note.folderId=null;note.updatedAt=now();}});data.folders=data.folders.filter(f=>f.id!==id);if(selectedFolder===id)selectedFolder='all';saveData();renderAll();toast('Map verwijderd; notities zijn bewaard.');
}

function bind(view){
  view.addEventListener('click',event=>{
    const folder=event.target.closest('[data-notes-folder]');if(folder){selectedFolder=folder.dataset.notesFolder;renderAll();return;}
    const note=event.target.closest('[data-note-open]');if(note){openNoteAction(note.dataset.noteOpen);return;}
    const rename=event.target.closest('[data-folder-rename]');if(rename){openFolderEditor(rename.dataset.folderRename);return;}
    const remove=event.target.closest('[data-folder-delete]');if(remove){deleteFolder(remove.dataset.folderDelete);return;}
    if(event.target.closest('#closeNoteAction')){closeNoteAction();return;}
    if(event.target.closest('#noteActionEdit,#noteActionOpen')){const id=actionNoteId;closeNoteAction();if(id)openNoteEditor(id);return;}
    if(event.target.closest('#noteActionMove')){moveActionNote();return;}
    if(event.target.closest('#noteActionPin')){toggleActionNotePin();return;}
    if(event.target.closest('#noteActionDelete')){deleteActionNote();return;}
    if(event.target.closest('#newNoteButton')){openNoteEditor();return;}
    if(event.target.closest('#newFolderButton')){openFolderEditor();return;}
    if(event.target.closest('#saveNoteButton')){saveNote();return;}
    if(event.target.closest('#deleteNoteButton')){deleteNote();return;}
    if(event.target.closest('#closeNoteEditor,#cancelNoteButton')){closeNoteEditor();return;}
    if(event.target.closest('#notePinButton')){setPinButton(!document.getElementById('notePinButton').classList.contains('active'));return;}
    if(event.target.closest('#saveFolderButton')){saveFolder();return;}
    if(event.target.closest('#closeFolderEditor,#cancelFolderButton')){closeFolderEditor();return;}
    if(event.target.id==='noteActionModal'){closeNoteAction();return;}
    if(event.target.id==='noteEditorModal'){closeNoteEditor();return;}
    if(event.target.id==='folderEditorModal'){closeFolderEditor();return;}
  });
  document.getElementById('notesSearch')?.addEventListener('input',event=>{searchTerm=event.target.value;renderNotes();});
  document.getElementById('noteBodyInput')?.addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key==='Enter'){event.preventDefault();saveNote();}});
  document.getElementById('folderNameInput')?.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();saveFolder();}});
  document.addEventListener('keydown',event=>{if(event.key!=='Escape')return;if(document.getElementById('noteActionModal')?.classList.contains('open'))closeNoteAction();else if(document.getElementById('folderEditorModal')?.classList.contains('open'))closeFolderEditor();else if(document.getElementById('noteEditorModal')?.classList.contains('open'))closeNoteEditor();});
  window.addEventListener('storage',event=>{if(event.key===NOTES_KEY){data=loadData();renderAll();}});
}

function handleWidgetOpen(){
  try{
    const params=new URLSearchParams(location.search);if(params.get('open')!=='note-new')return;
    const token=`${location.pathname}${location.search}`;
    if(sessionStorage.getItem(WIDGET_SESSION_KEY)===token)return;
    sessionStorage.setItem(WIDGET_SESSION_KEY,token);
    setTimeout(()=>openNoteEditor(),120);
  }catch{}
}

function boot(){
  installStyles();const view=installView();if(!view)return;bind(view);renderAll();handleWidgetOpen();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});else boot();
})();
