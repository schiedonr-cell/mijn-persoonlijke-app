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
  let booksHome = true;
  let searchQuery = '';
  let quickPickerFolder = null;
  let quickPickerType = null;
  let addingQuickFile = false;
  let editingId = null;
  let readingNoteId = null;
  let actionNoteId = null;
  let editingNotebookId = null;
  let choosingNotebookCoverId = null;
  let savingNotebookCover = false;

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
    // Bewaar een kleine cover in dezelfde bestaande notities-back-up.
    // Onbekende velden of onveilige URLs nooit in een img-src overnemen.
    const cover=typeof f?.cover==='string' && /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(f.cover)
      && f.cover.length<=135000 ? f.cover : '';
    return {id:f?.id||uid('folder'),name:String(f?.name||'Map').trim()||'Map',
      cover,createdAt:Number(f?.createdAt||Date.now())};
  }
  const DOCX_MIME='application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  const DOC_MIME='application/msword';
  function isWordMime(mime){return mime===DOCX_MIME||mime===DOC_MIME;}
  function isDocumentMime(mime){return mime==='application/pdf'||isWordMime(mime);}
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
      files:Array.isArray(n?.files)?n.files.filter(f=>f && (
        (typeof f.src==='string' && /^data:(?:image\/jpeg|image\/png|image\/webp|application\/pdf);base64,/.test(f.src)) ||
        (f.storage==='indexeddb' && isDocumentMime(f.mime) && Number(f.size)>0)
      )).map(f=>({
        id:String(f.id||uid('file')),name:String(f.name||'Bijlage'),
        mime:String(f.mime||''),src:typeof f.src==='string'?f.src:'',
        storage:f.storage==='indexeddb'?'indexeddb':'inline',size:Number(f.size)||0
      })):[],
      createdAt:Number(n?.createdAt||Date.now()),
      updatedAt:Number(n?.updatedAt||n?.createdAt||Date.now())
    };
  }
  function loadData() {
    // De eigen notitieopslag is leidend. Ook een lege lijst is een geldige,
    // expliciet opgeslagen toestand (bijvoorbeeld nadat alles is verwijderd).
    // Oude notities slechts één keer importeren, als deze opslag nog niet bestaat.
    let stored=null,raw={folders:[],notes:[]};
    try { stored=localStorage.getItem(STORE_KEY); } catch {}
    if(stored!==null){try{raw=JSON.parse(stored)||{};}catch{raw={folders:[],notes:[]};}}
    const out={
      folders:Array.isArray(raw.folders)?raw.folders.map(normalizeFolder):[],
      notes:Array.isArray(raw.notes)?raw.notes.map(normalizeNote):[]
    };

    if(stored===null){
      try {
        const main=JSON.parse(localStorage.getItem(MAIN_KEY)||'{}')||{};
        const old=Array.isArray(main.notes)?main.notes:[];
        const ids=new Set(out.notes.map(n=>n.id));
        old.forEach(n=>{
          const nn=normalizeNote(n);
          if(!ids.has(nn.id) && (nn.plainText||nn.title)){out.notes.push(nn);ids.add(nn.id);}
        });
      } catch {}
    }

    // Bestaande notities en mappen behouden; alleen daadwerkelijk lege
    // placeholders overslaan bij de eerste migratie.
    out.notes=out.notes.filter(n=>(n.title||'').trim()||(n.plainText||'').trim()||stripHtml(n.html||'')||(n.files||[]).length);
    if(stored===null || Array.isArray(raw.notes))saveData(out);
    return out;
  }
  function saveData(value=data) {
    try {
      const serialized=JSON.stringify(value);
      localStorage.setItem(STORE_KEY,serialized);
      return localStorage.getItem(STORE_KEY)===serialized;
    }catch{return false;}
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
      .notes-search{display:flex;align-items:center;gap:8px;margin:0 0 12px}
      .notes-search input{width:100%;min-width:0;min-height:52px;border:1px solid var(--line);border-radius:15px;padding:10px 13px;background:#fff;color:var(--text);font-size:16px}
      .notes-search input:focus{outline:2px solid var(--accent);outline-offset:1px}
      .notes-search button{min-height:48px;border:0;border-radius:13px;padding:8px 13px;background:var(--surface-soft);color:var(--accent-strong);font-weight:850}
      /* Nieuw eerste scherm: echte notitieboeken, geen extra navigatiemenu's. */
      .notes-books-top{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin:3px 0 17px}
      .notes-books-top strong{font-size:16px}
      .notes-books-top button{min-height:45px;border:1px solid var(--line);background:var(--surface);border-radius:13px;padding:8px 13px;font-weight:800;color:var(--accent-strong)}
      .notes-books-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px 17px;padding:4px 0 20px}
      .notes-book-item{min-width:0}
      .notes-book-open{width:100%;min-width:0;border:0;background:transparent;color:var(--text);padding:0;text-align:center;cursor:pointer}
      .notes-book-cover{position:relative;display:flex;align-items:center;justify-content:center;aspect-ratio:0.88;overflow:hidden;margin:0 7px 11px;border-radius:8px 16px 16px 8px;background:#e6dac3;box-shadow:4px 5px 13px rgba(29,32,29,.17),inset 5px 0 0 rgba(0,0,0,.12)}
      .notes-book-cover::before{content:'';position:absolute;inset:0 auto 0 9px;width:2px;background:rgba(27,33,27,.13);z-index:2}
      .notes-book-cover::after{content:'';position:absolute;inset:13px;border:1px solid rgba(46,54,46,.18);border-radius:5px 11px 11px 4px}
      .notes-book-cover .book-art{position:relative;z-index:1;display:flex;align-items:center;justify-content:center;width:60%;aspect-ratio:1;border:2px solid currentColor;border-radius:50%;font-size:clamp(21px,5vw,33px);font-weight:900;letter-spacing:.04em;opacity:.83}
      .notes-book-cover.book-tone-0{background:repeating-linear-gradient(0deg,#f3e4c8 0 18px,#d4bda0 19px,#f3e4c8 20px);color:#5b4234}
      .notes-book-cover.book-tone-1{background:repeating-linear-gradient(45deg,#1e3940 0 17px,#456168 17px 19px,#1e3940 19px 35px);color:#faf4e9}
      .notes-book-cover.book-tone-2{background:radial-gradient(circle at 24% 22%,#d3dec7 0 9%,transparent 9.5%),radial-gradient(circle at 68% 72%,#d3dec7 0 13%,transparent 13.5%),#729276;color:#153f2f}
      .notes-book-cover.book-tone-3{background:repeating-linear-gradient(0deg,#f5e7d2 0 25px,#b7c8c0 26px,#f5e7d2 27px);color:#845f66}
      .notes-book-cover.book-tone-4{background:repeating-linear-gradient(90deg,#51425c 0 22px,#796b89 24px,#51425c 26px);color:#faf0ef}
      .notes-book-cover.book-tone-5{background:linear-gradient(135deg,#d6be90,#f3eddc 55%,#d3b788);color:#63523e}
      .notes-book-title{display:block;font-size:17px;font-weight:850;line-height:1.3;overflow-wrap:anywhere}
      .notes-book-count{display:block;font-size:12px;color:var(--muted);margin-top:3px}
      .notes-book-item{position:relative}
      .notes-book-more{position:absolute;right:3px;top:3px;width:46px;height:46px;border:0;border-radius:14px;background:rgba(255,255,255,.95);color:#20332a;font-size:27px;font-weight:850;line-height:1;z-index:4;box-shadow:0 2px 9px #0002}
      .notes-book-cover.has-photo::after{display:none}
      .notes-book-cover.has-photo .book-art{display:none}
      .notes-book-cover-image{position:absolute;inset:0;display:block;width:100%;height:100%;object-fit:cover}
      .folder-manage-row{grid-template-columns:1fr auto}
      .folder-manage-row button{min-height:46px}
      .notes-book-edit-preview{display:flex;align-items:center;gap:14px;margin:14px 0 16px}
      .notes-book-edit-cover{width:112px;flex:0 0 112px;aspect-ratio:.88;border-radius:8px 13px 13px 8px;background:var(--surface-soft);overflow:hidden;display:grid;place-items:center;border:1px solid var(--line);font-size:15px;font-weight:800;color:var(--accent-strong)}
      .notes-book-edit-cover img{height:100%;width:100%;object-fit:cover}
      .notes-book-cover-buttons{display:grid;gap:8px;flex:1}
      .notes-book-cover-buttons button{min-height:52px;border:1px solid var(--line);border-radius:14px;background:#fff;color:var(--accent-strong);font-weight:850;padding:9px}
      .notes-book-cover-buttons button:disabled{opacity:.5}
      .notes-book-edit-actions{display:grid;gap:9px;margin-top:12px}
      .notes-book-edit-actions .primary-button{min-height:55px}
      .notes-book-delete{min-height:50px;border:1px solid #ead7d3;border-radius:14px;background:#fff7f5;color:var(--danger);font-weight:850}
      .notes-book-edit-note{font-size:12px;line-height:1.5;color:var(--muted)}
      .notes-books-actions{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:6px}
      .notes-books-actions button{min-height:52px;border:1px solid var(--line);background:var(--surface);border-radius:15px;color:var(--accent-strong);font-weight:850}
      .notes-book-bar{display:flex;align-items:center;justify-content:space-between;gap:9px;margin:0 0 15px}
      .notes-book-back{border:0;background:var(--surface-soft);color:var(--accent-strong);font-weight:850;border-radius:14px;min-height:50px;padding:9px 13px}
      .notes-book-add{border:0;background:var(--accent);color:#fff;font-weight:900;min-height:50px;border-radius:14px;padding:9px 17px}
      /* Eén grote plusknop met keuzemenu, zonder direct het notitieformulier te openen. */
      .note-create-backdrop{position:fixed;inset:0;background:rgba(24,28,25,.35);z-index:70}
      .note-create-backdrop[hidden],.note-create-menu[hidden]{display:none!important}
      .note-create-fab{position:fixed;right:max(18px,calc((100vw - 680px)/2 + 18px));bottom:calc(90px + env(safe-area-inset-bottom));width:72px;height:72px;border-radius:50%;border:0;background:var(--accent);color:#fff;font-size:43px;font-weight:500;line-height:1;box-shadow:0 5px 17px rgba(20,35,25,.27);z-index:72;cursor:pointer}
      .note-create-fab[aria-expanded="true"]{background:#303b32;transform:rotate(45deg)}
      .note-create-menu{position:fixed;right:max(18px,calc((100vw - 680px)/2 + 18px));bottom:calc(178px + env(safe-area-inset-bottom));width:min(344px,calc(100vw - 36px));max-height:min(440px,60dvh);overflow-y:auto;z-index:71;border-radius:24px;background:#28302b;color:white;box-shadow:0 15px 40px rgba(0,0,0,.32);padding:20px 17px 17px}
      .note-create-menu h3{font-size:17px;font-weight:850;margin:0 0 18px;color:#fff}
      .note-create-choices{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px 8px}
      .note-create-choice{min-width:0;min-height:96px;display:flex;flex-direction:column;align-items:center;gap:9px;padding:0 2px;background:transparent;border:0;color:#fff;font-size:13px;font-weight:650;cursor:pointer}
      .note-create-icon{display:grid;place-items:center;border-radius:50%;width:61px;height:61px;background:#101612;color:#fff;font-size:28px;font-weight:800}
      .note-create-icon.file{font-size:15px;letter-spacing:-.02em}
      .note-create-choice:focus-visible,.note-create-fab:focus-visible{outline:3px solid #a5c3ac;outline-offset:3px}
      @media(max-height:610px){.note-create-fab{bottom:calc(22px + env(safe-area-inset-bottom))}.note-create-menu{bottom:calc(105px + env(safe-area-inset-bottom))}}
      .notes-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px 12px}
      @media(min-width:640px){.notes-books-grid{grid-template-columns:repeat(3,minmax(0,1fr));}.notes-grid{grid-template-columns:repeat(3,minmax(0,1fr));}}
      #notesBooksHome[hidden],#notesBookContents[hidden]{display:none!important}
      .note-v2-card{position:relative;min-width:0;border:0;border-radius:11px;background:transparent;padding:0;box-shadow:none}
      .note-v2-card.pinned .note-v2-cover{box-shadow:0 0 0 2px var(--accent)}
      .note-v2-cover{height:auto;aspect-ratio:1;overflow:hidden;border-radius:9px;background:#f7edbc;box-shadow:0 2px 7px rgba(26,30,26,.13);position:relative;text-align:left}
      .note-v2-cover.note-cover-pdf{background:#c8442c;color:#fff;display:flex;flex-direction:column;justify-content:space-between;padding:13px}
      .note-v2-cover.note-cover-word{background:linear-gradient(140deg,#e9f0fb,#c4d9f4);color:#143965;display:flex;flex-direction:column;justify-content:space-between;padding:13px}
      .note-v2-cover.note-cover-word strong{font-size:15px;line-height:1.3;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden}
      .note-word-mark{font-size:36px;font-weight:900;align-self:center;line-height:1.1;margin:auto}
      .note-v2-cover.note-cover-pdf strong{font-size:16px;overflow-wrap:anywhere;line-height:1.3;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden}
      .note-v2-cover.note-cover-pdf.has-preview{background:#e7e8e4;padding:0}
      .note-v2-cover.note-cover-pdf.has-preview strong,.note-v2-cover.note-cover-pdf.has-preview .note-pdf-mark{display:none}
      .note-pdf-page{display:block;position:absolute;inset:0;width:100%;height:100%;object-fit:contain;background:#ebeeeb}
      .note-pdf-page[hidden]{display:none!important}
      .note-pdf-mark{font-size:38px;font-weight:950;align-self:center;line-height:1.1;margin:auto}
      .note-v2-cover.note-cover-photo img{display:block;width:100%;height:100%;object-fit:cover}
      .note-v2-cover.note-cover-text{background:#faedac;padding:13px 11px;overflow:hidden}
      .note-cover-text-title{font-size:15px;font-weight:850;line-height:1.25;max-height:60px;overflow:hidden;word-break:break-word}
      .note-cover-text-body{margin-top:10px;color:#3d402f;font-size:13px;line-height:1.4;max-height:130px;overflow:hidden;white-space:pre-wrap;overflow-wrap:anywhere}
      .note-v2-caption{padding:8px 2px 0;display:flex;justify-content:space-between;gap:3px;align-items:flex-start}
      .note-v2-caption .note-v2-title{font-size:14px;line-height:1.3;margin:0;flex:1;min-width:0;max-height:40px;overflow:hidden;overflow-wrap:anywhere}
      .note-v2-stamp{position:absolute;right:8px;top:8px;background:#fff;border-radius:50%;padding:4px 6px;font-size:11px;box-shadow:0 1px 5px #0002}
      .note-v2-card .note-v2-more{width:39px;height:39px;flex:0 0 39px;border-radius:50%;background:#e7e9e2}
      .note-v2-card .note-v2-pin{width:34px;height:34px;flex:0 0 34px;font-size:16px;border-radius:11px}
      .notes-v2-shell{max-width:680px;margin:0 auto}
      .note-v2-top{display:block}
      .note-v2-open{border:0;background:transparent;color:var(--text);padding:0;text-align:left;min-width:0;width:100%;cursor:pointer}
      .note-v2-title{font-size:16px;font-weight:900;line-height:1.3;margin-bottom:5px}
      .note-v2-preview{font-size:14px;line-height:1.5;color:var(--text);max-height:96px;overflow:hidden;word-break:break-word}
      .note-v2-preview ul,.note-v2-preview ol{padding-left:21px;margin:4px 0}
      .note-v2-preview p,.note-v2-preview div{margin:2px 0}
      .note-v2-preview h3{font-size:16px;font-weight:900;margin:5px 0}
      .note-v2-tools{display:flex;align-items:flex-start;gap:6px}
      .note-v2-pin,.note-v2-more{width:42px;height:42px;flex:0 0 42px;border:0;border-radius:12px;background:var(--surface-soft);color:var(--accent-strong);font-size:20px;cursor:pointer}
      .note-v2-more{font-size:25px;font-weight:850;line-height:1}
      .note-action-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}
      .note-action-grid button{min-height:54px;border-radius:14px;font-weight:850;padding:10px 12px}
      .note-action-delete{grid-column:1/-1;border:1px solid #ead7d3;background:#fff7f5;color:var(--danger)}
      .note-action-title{overflow-wrap:anywhere}

      .note-v2-meta{display:flex;align-items:center;gap:7px;flex-wrap:wrap;color:var(--muted);font-size:11px;margin-top:5px}
      .note-folder-badge{background:var(--accent-soft);color:var(--accent-strong);border-radius:999px;padding:5px 8px;font-weight:800}
      /* Alleen-lezen: een rustige notitiepagina, niet meteen de editor. */
      #noteReaderModal{padding:0;align-items:stretch;background:var(--surface);z-index:102}
      #noteReaderModal .note-reader-sheet{width:100%;max-width:680px;max-height:100dvh;height:100dvh;border-radius:0;padding:0;display:flex;flex-direction:column;overflow:hidden;box-shadow:none;background:var(--surface)}
      .note-reader-top{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:calc(12px + env(safe-area-inset-top)) 16px 12px;border-bottom:1px solid var(--line)}
      .note-reader-top button{min-height:50px;border:0;border-radius:14px;padding:9px 14px;font-weight:850;font-size:15px;cursor:pointer}
      .note-reader-back{background:var(--surface-soft);color:var(--accent-strong)}
      .note-reader-edit{background:var(--accent);color:#fff}
      .note-reader-scroll{overflow-y:auto;flex:1;overscroll-behavior:contain;padding:26px 21px calc(28px + env(safe-area-inset-bottom))}
      .note-reader-heading{font-size:clamp(23px,6vw,30px);line-height:1.22;overflow-wrap:anywhere;margin:0 0 10px;color:var(--text)}
      .note-reader-meta{font-size:13px;color:var(--muted);margin-bottom:25px}
      .note-reader-content{font-size:17px;line-height:1.72;color:var(--text);overflow-wrap:anywhere}
      .note-reader-content:empty{display:none}
      .note-reader-content p,.note-reader-content div{margin:0 0 13px}
      .note-reader-content h3{font-size:21px;line-height:1.35;margin:20px 0 10px}
      .note-reader-content ul,.note-reader-content ol{padding-left:25px;margin:10px 0 18px}
      .note-reader-content li{margin:5px 0}
      .note-reader-files{display:grid;gap:11px;margin-top:20px}
      .note-reader-files:empty{display:none}
      .note-reader-file{display:flex;align-items:center;gap:12px;min-height:72px;padding:10px;border:1px solid var(--line);border-radius:16px;background:var(--surface-soft);color:var(--text);font-weight:800;text-align:left;overflow-wrap:anywhere;font-size:15px}
      .note-reader-file-thumb{display:flex;align-items:center;justify-content:center;width:58px;height:58px;flex:none;overflow:hidden;border-radius:10px;background:#b9422d;color:#fff;font-size:13px;font-weight:900}
      .note-reader-file-thumb img{width:100%;height:100%;object-fit:cover}
      .note-reader-file-thumb.word{background:#315d9a}
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
      .note-rich-editor h3{font-size:21px;font-weight:900;line-height:1.3;margin:12px 0 6px}
      .note-rich-editor p{margin:7px 0}
      #noteEditorModal{padding-top:max(18px, env(safe-area-inset-top))}
      #noteEditorModal .note-editor-sheet{max-height:calc(100dvh - 70px);overscroll-behavior:contain}
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
      .note-files-panel{border-top:1px solid var(--line);margin-top:15px;padding-top:13px}
      .note-files-head{display:flex;justify-content:space-between;align-items:center;gap:9px}
      .note-file-add{min-height:46px;padding:8px 14px;border:0;border-radius:13px;background:var(--accent-soft);color:var(--accent-strong);font-weight:800}
      .note-file-list{display:grid;gap:8px;margin-top:11px}
      .note-file-row{display:grid;grid-template-columns:minmax(0,1fr) 44px;gap:8px;align-items:center;border:1px solid var(--line);padding:7px;border-radius:13px}
      .note-file-open{border:0;background:transparent;text-align:left;display:flex;align-items:center;gap:9px;min-height:48px;min-width:0;color:var(--text);font-weight:750}
      .note-file-thumb{width:44px;height:44px;flex:none;border-radius:9px;object-fit:cover;background:var(--surface-soft)}
      .note-file-remove{width:40px;height:40px;border:0;border-radius:10px;background:#fff1ef;color:var(--danger)}
      .note-file-hint{color:var(--muted);font-size:12px;line-height:1.5;margin:7px 0 0}
      /* Fallback-galerij: normale fullscreen foto op donkere achtergrond. */
      #noteImageModal{background:#101310;align-items:stretch;padding:0;z-index:180}
      #noteImageModal .note-photo-sheet{width:100%;height:100dvh;max-width:none;max-height:none;display:flex;flex-direction:column;padding:0;background:#101310;border-radius:0}
      #noteImageModal .sheet-head{padding:calc(12px + env(safe-area-inset-top)) 16px 12px;color:#fff}
      #noteImageModal .sheet-head h3{color:#fff}
      #noteImageModal .close-button{color:#fff;background:#303a32}
      .note-image-viewport{height:auto;flex:1;overflow:auto;text-align:center;background:#101310;margin:0;display:flex;align-items:center;justify-content:center}
      .note-image-viewport img{display:block;max-width:100%;max-height:100%;height:auto;width:auto!important;object-fit:contain}
      .note-image-zoom{display:none}
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
          <div class="screen-title-wrap">
            <h2 id="notesScreenTitle">Notitieboeken</h2>
            <p id="notesScreenCaption">Je notities, foto's en documenten bij elkaar.</p>
          </div>
        </header>

        <div id="notesBooksHome">
          <div class="notes-books-top">
            <button type="button" id="notesBooksSearchButton">Zoeken</button>
          </div>
          <div class="notes-books-grid" id="notesBooksGrid"></div>
          <div class="notes-books-actions">
            <button type="button" id="quickNewFolderButton">+ Notitieboek</button>
            <button type="button" id="openFoldersButton">Beheren</button>
          </div>
        </div>

        <div id="notesBookContents" hidden>
          <div class="notes-book-bar">
            <button type="button" class="notes-book-back" id="notesBackToBooks">← Notitieboeken</button>
          </div>
          <div class="notes-search">
            <input type="search" id="notesSearchInput" aria-label="Notities zoeken" placeholder="Zoek in notities…" autocomplete="off" />
            <button type="button" id="notesSearchClear" hidden>Wissen</button>
          </div>
          <div class="section-heading">
            <div><h3 id="notesListTitle">Inhoud</h3><span class="small-muted">Tik op een tegel om te openen</span></div>
            <span class="small-muted" id="noteV2Counter">0</span>
          </div>
          <div class="notes-grid" id="noteV2List"></div>
          <div class="note-create-backdrop" id="noteCreateBackdrop" hidden></div>
          <div class="note-create-menu" id="noteCreateMenu" role="dialog" aria-label="Toevoegen aan dit notitieboek" hidden>
            <h3>Wat wil je toevoegen?</h3>
            <div class="note-create-choices">
              <button type="button" class="note-create-choice" data-note-quick="write">
                <span class="note-create-icon">✎</span><span>Schrijven</span>
              </button>
              <button type="button" class="note-create-choice" data-note-quick="checklist">
                <span class="note-create-icon">☑</span><span>Checklist</span>
              </button>
              <button type="button" class="note-create-choice" data-note-quick="camera">
                <span class="note-create-icon">⌾</span><span>Camera</span>
              </button>
              <button type="button" class="note-create-choice" data-note-quick="photo">
                <span class="note-create-icon">▧</span><span>Foto kiezen</span>
              </button>
              <button type="button" class="note-create-choice" data-note-quick="pdf">
                <span class="note-create-icon file">PDF</span><span>Document</span>
              </button>
            </div>
          </div>
          <button type="button" class="note-create-fab" id="noteCreateFab" aria-label="Toevoegen" aria-expanded="false" aria-controls="noteCreateMenu">+</button>
          <input type="file" id="noteQuickCameraInput" accept="image/*" capture="environment" style="display:none" />
          <input type="file" id="noteQuickPhotoInput" accept="image/*" style="display:none" />
          <input type="file" id="noteQuickPdfInput" accept="application/pdf,.pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx,application/msword,.doc" style="display:none" />
        </div>
      </div>
    `;

    if(!document.getElementById('noteEditorModal')){
      document.body.insertAdjacentHTML('beforeend',`
        <div class="modal-backdrop" id="noteReaderModal" role="dialog" aria-modal="true" aria-labelledby="noteReaderTitle">
          <div class="sheet note-reader-sheet">
            <div class="note-reader-top">
              <button type="button" class="note-reader-back" id="closeNoteReader">← Terug</button>
              <button type="button" class="note-reader-edit" id="editNoteReader">Bewerken</button>
            </div>
            <div class="note-reader-scroll" id="noteReaderScroll">
              <h2 class="note-reader-heading" id="noteReaderTitle">Notitie</h2>
              <div class="note-reader-meta" id="noteReaderMeta"></div>
              <article class="note-reader-content" id="noteReaderContent"></article>
              <div class="note-reader-files" id="noteReaderFiles"></div>
            </div>
          </div>
        </div>
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
              <button class="note-format-button" data-note-block="h3" title="Kopje">Kop</button>
               <button class="note-format-button" data-note-block="p" title="Gewone tekst">Tekst</button>
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
            <div class="note-files-panel">
              <div class="note-files-head"><strong>Foto's en PDF's</strong>
                <div style="display:flex;gap:7px;flex-wrap:wrap;justify-content:end">
                  <button type="button" class="note-file-add" id="noteCameraButton">Camera</button>
                  <button type="button" class="note-file-add" id="noteAddFileButton">+ Kiezen</button>
                </div>
              </div>
              <input type="file" id="noteFileInput" accept="image/*,application/pdf,.pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx,application/msword,.doc" style="display:none" />
              <input type="file" id="noteCameraInput" accept="image/*" capture="environment" style="display:none" />
              <div class="note-file-list" id="noteFileList"></div>
              <p class="note-file-hint">Foto's, PDF's en Word-bestanden bij je notitie. PDF of Word maximaal 8 MB per bestand; bijlagen komen mee in de back-up.</p>
            </div>

            <div class="note-editor-actions" id="noteEditorActions">
              <button class="primary-button" id="saveRichNoteButton">Notitie bewaren</button>
              <button class="note-delete-button" id="deleteRichNoteButton" style="display:none;">Verwijderen</button>
            </div>
          </div>
        </div>

        <div class="modal-backdrop" id="noteImageModal" role="dialog" aria-modal="true" aria-label="Foto bekijken">
          <div class="sheet note-photo-sheet">
            <div class="sheet-head"><h3>Foto bekijken</h3><button class="close-button" id="closeNoteImage" aria-label="Sluiten">×</button></div>
            <div class="note-image-viewport" id="noteImageViewport"><img id="noteImagePreview" alt="Bijlage" /></div>
            <div class="note-image-zoom"><button id="noteZoomOut" type="button">− Kleiner</button><button id="noteZoomIn" type="button">+ Groter</button></div>
          </div>
        </div>
        <div class="modal-backdrop" id="noteActionModal" role="dialog" aria-modal="true" aria-labelledby="noteActionHeading">
          <div class="sheet">
            <div class="sheet-head">
              <div><h3 id="noteActionHeading" class="note-action-title">Notitie</h3><p class="small-muted" style="margin:5px 0 0">Wat wil je met deze notitie doen?</p></div>
              <button class="close-button" id="closeNoteAction" aria-label="Sluiten">×</button>
            </div>
            <div class="note-action-grid">
              <button class="primary-button" id="noteActionEdit">Bewerken</button>
              <button class="secondary-button" id="noteActionPin">Vastzetten</button>
              <button class="note-action-delete" id="noteActionDelete">Verwijderen</button>
            </div>
          </div>
        </div>
        <div class="modal-backdrop" id="noteBookEditModal" role="dialog" aria-modal="true" aria-labelledby="noteBookEditHeading">
          <div class="sheet">
            <div class="sheet-head">
              <div><h3 id="noteBookEditHeading">Notitieboek bewerken</h3>
                <p class="small-muted" style="margin:5px 0 0;">Naam en voorpagina aanpassen.</p></div>
              <button type="button" class="close-button" id="closeNoteBookEdit" aria-label="Sluiten">×</button>
            </div>
            <label for="noteBookEditName" class="field-label">Naam</label>
            <input type="text" id="noteBookEditName" class="text-input" maxlength="70" />
            <div class="notes-book-edit-preview">
              <div class="notes-book-edit-cover" id="noteBookEditPreview" aria-label="Huidige omslag">Standaard</div>
              <div class="notes-book-cover-buttons">
                <button type="button" id="noteBookChooseCover">Foto kiezen</button>
                <button type="button" id="noteBookResetCover">Standaardomslag</button>
              </div>
            </div>
            <input type="file" id="noteBookCoverInput" accept="image/*" style="display:none" />
            <p class="notes-book-edit-note">De foto wordt verkleind en meegenomen in je bestaande back-up.</p>
            <div class="notes-book-edit-actions">
              <button type="button" class="primary-button" id="noteBookSaveName">Naam bewaren</button>
              <button type="button" class="notes-book-delete" id="noteBookDelete">Notitieboek verwijderen</button>
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
    // Oude Android-wrappers hebben nog geen bestandskiezer. Laat hun bestaande
    // notitiebediening ongemoeid totdat de nieuwe variant beschikbaar is.
    const filePanel=document.querySelector('.note-files-panel');
    if(filePanel && window.AndroidWidgetBridge && typeof window.AndroidWidgetBridge.openNotePdf!=='function')
      filePanel.style.display='none';
  }

  function toggleCreateMenu(open){
    const panel=document.getElementById('noteCreateMenu');
    const backdrop=document.getElementById('noteCreateBackdrop');
    const fab=document.getElementById('noteCreateFab');
    if(!panel||!backdrop||!fab)return;
    const show=Boolean(open&&!booksHome);
    panel.hidden=!show;
    backdrop.hidden=!show;
    fab.setAttribute('aria-expanded',String(show));
  }
  function openNotebook(id){
    toggleCreateMenu(false);
    // Gebruik de bestaande map-ID's: geen migratie of duplicatie van gegevens.
    if(id!==ALL && id!==GENERAL && !data.folders.some(f=>f.id===id))return;
    activeFolder=id;
    booksHome=false;
    searchQuery='';
    const input=document.getElementById('notesSearchInput');
    if(input)input.value='';
    renderNotes();
  }
  function backToNotebooks(){
    toggleCreateMenu(false);
    booksHome=true;
    activeFolder=ALL;
    searchQuery='';
    const input=document.getElementById('notesSearchInput');
    if(input)input.value='';
    renderNotes();
  }
  function renderFolderStrip(){
    const books=document.getElementById('notesBooksGrid');
    if(!books)return;
    // Losse notities krijgen altijd een eigen boek; niets raakt zoek of kwijt.
    const sources=[
      ...data.folders,
      {id:GENERAL,name:'Losse notities',cover:''}
    ];
    books.innerHTML=sources.map((book,i)=>{
      const count=data.notes.filter(n=>book.id===GENERAL?!n.folderId:n.folderId===book.id).length;
      const mark=book.id===GENERAL?'N':book.name.trim().slice(0,3).toLocaleUpperCase('nl');
      const image=book.cover ? `<img class="notes-book-cover-image" src="${esc(book.cover)}" alt="" />` : '';
      return `<div class="notes-book-item">
        <button type="button" class="notes-book-open" data-notebook-open="${esc(book.id)}" aria-label="${esc(book.name)} openen, ${count} notities">
          <span class="notes-book-cover book-tone-${i%6}${image?' has-photo':''}">
            ${image}<span class="book-art">${esc(mark)}</span>
          </span>
          <span class="notes-book-title">${esc(book.name)}</span>
          <span class="notes-book-count">${count} ${count===1?'notitie':'notities'}</span>
        </button>
        ${book.id===GENERAL?'':`<button type="button" class="notes-book-more"
          data-notebook-edit="${esc(book.id)}" aria-label="${esc(book.name)} bewerken">⋯</button>`}
      </div>`;
    }).join('');
    const title=booksHome?'Notitieboeken':(activeFolder===ALL?'Alle notities':(activeFolder===GENERAL?'Losse notities':folderName(activeFolder)));
    const screenTitle=document.getElementById('notesScreenTitle');
    if(screenTitle)screenTitle.textContent=title;
    const caption=document.getElementById('notesScreenCaption');
    if(caption)caption.textContent=booksHome?'Kies een boek om je notities te bekijken.':'Foto\'s, PDF\'s, lijstjes en tekst bij elkaar.';
    const listTitle=document.getElementById('notesListTitle');
    if(listTitle)listTitle.textContent=searchQuery?'Zoekresultaten':'Inhoud';
    const home=document.getElementById('notesBooksHome');
    const contents=document.getElementById('notesBookContents');
    if(home)home.hidden=!booksHome;
    if(contents)contents.hidden=booksHome;
  }

  function standaloneAttachment(note){
    if(!note || !Array.isArray(note.files) || note.files.length!==1)return null;
    const text=String(note.plainText||'').trim()||stripHtml(note.html||'').trim();
    if(text)return null;
    const file=note.files[0];
    return file && (isDocumentMime(file.mime)||file.mime?.startsWith('image/'))?file:null;
  }
  function noteCover(note){
    const files=note.files||[];
    const photo=files.find(f=>f.mime?.startsWith('image/') && /^data:image\//.test(f.src||''));
    if(photo)return `<div class="note-v2-cover note-cover-photo">
      <img src="${esc(photo.src)}" alt="Foto bij ${esc(noteTitle(note))}" loading="lazy" />
    </div>`;
    const pdf=files.find(f=>f.mime==='application/pdf');
    if(pdf)return `<div class="note-v2-cover note-cover-pdf">
      <strong>${esc(pdf.name)}</strong><span class="note-pdf-mark">PDF</span>
      <img class="note-pdf-page" data-pdf-preview-id="${esc(pdf.id)}"
        alt="Eerste pagina van ${esc(pdf.name)}" loading="lazy" hidden />
    </div>`;
    const word=files.find(f=>isWordMime(f.mime));
    if(word)return `<div class="note-v2-cover note-cover-word">
      <strong>${esc(word.name)}</strong><span class="note-word-mark">${word.mime===DOCX_MIME?'DOCX':'DOC'}</span>
    </div>`;
    return `<div class="note-v2-cover note-cover-text">
      <div class="note-cover-text-title">${esc(noteTitle(note))}</div>
      <div class="note-cover-text-body">${esc(note.plainText || stripHtml(note.html||''))}</div>
    </div>`;
  }
  function renderNotes() {
    replaceNotesView();
    renderFolderStrip();
    const list=document.getElementById('noteV2List');
    const counter=document.getElementById('noteV2Counter');
    if(!list || booksHome)return;
    let notes=data.notes.slice();
    if(activeFolder===GENERAL)notes=notes.filter(n=>!n.folderId);
    else if(activeFolder!==ALL)notes=notes.filter(n=>n.folderId===activeFolder);
    const needle=searchQuery.trim().toLocaleLowerCase('nl');
    if(needle)notes=notes.filter(n=>[
      n.title,n.plainText,folderName(n.folderId),...(n.files||[]).map(f=>f.name)
    ].some(v=>String(v||'').toLocaleLowerCase('nl').includes(needle)));
    notes.sort((a,b)=>(Number(b.pinned)-Number(a.pinned)) || (b.updatedAt-a.updatedAt));
    if(counter)counter.textContent=String(notes.length);
    const clear=document.getElementById('notesSearchClear');
    if(clear)clear.hidden=!searchQuery;
    list.innerHTML=notes.length?notes.map(n=>`
      <article class="note-v2-card ${n.pinned?'pinned':''}">
        <button type="button" class="note-v2-open" data-rich-note-open="${esc(n.id)}">
          ${noteCover(n)}
        </button>
        <div class="note-v2-caption">
          <div class="note-v2-title">${esc(noteTitle(n))}</div>
          <button type="button" class="note-v2-more" data-rich-note-action="${esc(n.id)}" aria-label="Acties voor ${esc(noteTitle(n))}">⋯</button>
        </div>
        <div class="note-v2-meta">
          <span>${esc(formatDate(n.updatedAt))}</span>
          ${n.pinned?'<span>Vastgezet</span>':''}
          ${n.files?.length?'<span> · '+n.files.length+' bijlage(n)</span>':''}
        </div>
      </article>
    `).join(''):`<div class="empty-state">${needle?'Geen notities gevonden.':'Dit notitieboek is nog leeg. Tik op + Nieuw om iets toe te voegen.'}</div>`;
    // Alleen zichtbare PDF-tegels; ook bestaande inline PDF's blijven intact.
    window.MijnDagPdfPreviews?.scan(notes);
  }

  function folderOptions(selected=null) {
    return `<option value="">Algemeen</option>`+data.folders.map(f=>`<option value="${esc(f.id)}" ${selected===f.id?'selected':''}>${esc(f.name)}</option>`).join('');
  }


  let imageZoom=1;
  function renderFileList(note){
    const list=document.getElementById('noteFileList');
    if(!list)return;
    const files=note?.files||[];
    list.innerHTML=files.map(f=>`
      <div class="note-file-row">
        <button type="button" class="note-file-open" data-note-file-open="${esc(f.id)}">
          ${isDocumentMime(f.mime)?`<span class="note-file-thumb" style="display:grid;place-items:center;font-size:12px;font-weight:900">${f.mime==='application/pdf'?'PDF':(f.mime===DOCX_MIME?'DOCX':'DOC')}</span>`:`<img class="note-file-thumb" alt="" src="${f.src}">`}
          <span style="overflow-wrap:anywhere">${esc(f.name)}</span>
        </button>
        <button type="button" class="note-file-remove" data-note-file-remove="${esc(f.id)}" aria-label="Bestand verwijderen">×</button>
      </div>`).join('');
    if(!note)list.innerHTML='<p class="note-file-hint">Je kunt direct een foto of PDF toevoegen, ook aan een nieuwe notitie.</p>';
    else if(!files.length)list.innerHTML='<p class="note-file-hint">Nog geen bestanden bij deze notitie.</p>';
  }
  function readFileAsDataURL(file){
    return new Promise((resolve,reject)=>{
      const reader=new FileReader();
      reader.onload=()=>resolve(String(reader.result||''));
      reader.onerror=()=>reject(new Error('Bestand lezen mislukt'));
      reader.readAsDataURL(file);
    });
  }
  async function prepareNoteFile(file){
    const name=String(file?.name||'').trim(),lower=name.toLowerCase();
    // Drive geeft een PDF soms een leeg/generiek MIME-type.
    // Controleer de bestandshandtekening in plaats van alleen de naam.
    let signature;
    try{signature=new Uint8Array(await file.slice(0,32).arrayBuffer());}
    catch{throw new Error('Bestand uit Drive niet leesbaar. Download het eerst naar je telefoon.');}
    const pdf=String.fromCharCode(...signature.subarray(0,5))==='%PDF-';
    const zip=signature[0]===0x50&&signature[1]===0x4b;
    const oldDoc=signature[0]===0xd0&&signature[1]===0xcf&&signature[2]===0x11&&signature[3]===0xe0;
    let mime=null;
    if(pdf)mime='application/pdf';
    else if(zip && (/\\.docx$/i.test(name)||file.type===DOCX_MIME))mime=DOCX_MIME;
    else if(oldDoc && (/\\.doc$/i.test(name)||file.type===DOC_MIME))mime=DOC_MIME;
    else if(/\\.(pdf|docx?|gdoc)$/i.test(name)||String(file.type||'').includes('google-apps')){
      throw new Error('Dit document is geen leesbare PDF of Word. Download/exporteer het vanuit Drive als PDF of Word.');
    }
    if(mime){
      if(!file.size)throw new Error('Het gekozen document is leeg.');
      if(file.size>8*1024*1024)
        throw new Error('Dit document is '+(file.size/1024/1024).toFixed(1).replace('.',',')+' MB. De grens is nu 8 MB. Kies een kleinere PDF of Word.');
      const isPdf=mime==='application/pdf';
      // Op oudere Android-versies kleine PDF's blijven tonen zoals eerst.
      const binaryStorage=!isPdf || file.size>1200000 ||
        (file.size>300000 && (!window.AndroidWidgetBridge ||
          typeof window.AndroidWidgetBridge.beginNotePdf==='function'));
      if(binaryStorage){
        if(!window.MijnDagNoteBlobStorage)throw new Error('Bestandsopslag is niet beschikbaar.');
        if(window.AndroidWidgetBridge && isPdf && typeof window.AndroidWidgetBridge.beginNotePdf!=='function')
          throw new Error('Voor grotere PDF\'s is eerst een Mijn dag-update nodig.');
        if(window.AndroidWidgetBridge && !isPdf && typeof window.AndroidWidgetBridge.beginNoteDocument!=='function')
          throw new Error('Voor Word-bestanden is eerst de Mijn dag-update nodig.');
        const bytesInUse=data.notes.reduce((total,n)=>total+(n.files||[])
          .filter(f=>f.storage==='indexeddb').reduce((sum,f)=>sum+Number(f.size||0),0),0);
        if(bytesInUse+file.size>10*1024*1024)
          throw new Error('Bijlagenopslag vol (10 MB). Maak eerst een back-up en ruim op.');
        return {id:uid('file'),name:name||(isPdf?'Document.pdf':'Document.docx'),mime,
          size:file.size,storage:'indexeddb',src:'',
          blob:file.type===mime?file:new Blob([file],{type:mime})};
      }
      const raw=await readFileAsDataURL(file);
      return {id:uid('file'),name:name||'Document.pdf',mime,size:file.size,
        src:'data:application/pdf;base64,'+raw.split(',')[1]};
    }
    // Google Drive levert foto's soms met een leeg of algemeen MIME-type
    // (application/octet-stream). Controleer daarom ook de echte bytes.
    const imageType=String(file.type||'').toLowerCase();
    const jpeg=signature[0]===0xff && signature[1]===0xd8 && signature[2]===0xff;
    const png=signature[0]===0x89 && String.fromCharCode(...signature.slice(1,4))==='PNG';
    const gif=String.fromCharCode(...signature.slice(0,3))==='GIF';
    const webp=String.fromCharCode(...signature.slice(0,4))==='RIFF' &&
       String.fromCharCode(...signature.slice(8,12))==='WEBP';
    const heif=String.fromCharCode(...signature.slice(4,8))==='ftyp' &&
       /^(heic|heix|hevc|mif1|msf1|avif)/.test(String.fromCharCode(...signature.slice(8,12)));
    const knownPhoto=jpeg||png||gif||webp||heif;
    const photoHint=/\.(jpe?g|png|gif|webp|heic|heif|avif)$/i.test(name) ||
       (/^image\/(jpeg|png|gif|webp|heic|heif|avif)$/.test(imageType));
    if(!knownPhoto&&!photoHint)
       throw new Error('Dit is geen leesbare foto. Kies een JPG, PNG of andere afbeelding uit Drive.');
    if(!file.size)throw new Error('Deze foto uit Drive is leeg. Download haar eerst naar je telefoon.');
    if(file.size>30000000)throw new Error('Foto te groot (maximaal 30 MB). Download een kleinere versie.');
    const url=URL.createObjectURL(file);
    try{
      const img=new Image();
      await new Promise((resolve,reject)=>{
        img.onload=resolve;
        img.onerror=()=>reject(new Error('Drive leverde een afbeelding die dit toestel niet kan openen. Download of exporteer haar als JPG of PNG.'));
        img.src=url;
      });
      const canvas=document.createElement('canvas');
      const ctx=canvas.getContext('2d');
      if(!ctx)throw new Error('Fotobewerking is op dit toestel niet beschikbaar.');
      let src='';
      for(const maxSide of [1280,1080,900,720,560]){
        const scale=Math.min(1,maxSide/Math.max(img.naturalWidth,img.naturalHeight));
        canvas.width=Math.max(1,Math.round(img.naturalWidth*scale));
        canvas.height=Math.max(1,Math.round(img.naturalHeight*scale));
        ctx.fillStyle='#fff';
        ctx.fillRect(0,0,canvas.width,canvas.height);
        ctx.drawImage(img,0,0,canvas.width,canvas.height);
        for(const quality of [.72,.56,.42]){
          src=canvas.toDataURL('image/jpeg',quality);
          if(src.startsWith('data:image/jpeg;base64,') && src.length<=850000)break;
        }
        if(src.startsWith('data:image/jpeg;base64,') && src.length<=850000)break;
      }
      if(!src.startsWith('data:image/jpeg;base64,') || src.length>850000)
        throw new Error('Foto uit Drive blijft te groot. Kies een kleinere afbeelding.');
      return {id:uid('file'),name:name||'Foto.jpg',mime:'image/jpeg',
        size:Math.round((src.length-src.indexOf(',')-1)*.75),src};
    }finally{URL.revokeObjectURL(url);}
  }
  let addingNoteFile=false;
  async function addNoteFile(file){
    if(!file||addingNoteFile)return;
    const startedWith=editingId;
    const existing=startedWith?data.notes.find(n=>n.id===startedWith):null;
    if(startedWith&&!existing){toast('Notitie niet gevonden.');return;}
    if(existing&&(existing.files||[]).length>=12){toast('Maximaal 12 bestanden in deze proefversie.');return;}
    addingNoteFile=true;
    const buttons=['noteAddFileButton','noteCameraButton'].map(id=>document.getElementById(id));
    buttons.forEach(button=>{if(button)button.disabled=true;});
    try{
      const attachment=await prepareNoteFile(file);
      if(editingId!==startedWith)return;
      let persistedBlob=false,committed=false;
      try{
        if(attachment.storage==='indexeddb'){
          await window.MijnDagNoteBlobStorage.put(attachment.id,attachment.blob);
          delete attachment.blob;
          persistedBlob=true;
          if(editingId!==startedWith)return;
        }
      let note=existing;
      if(!note){
        // Een bijlage bij een nieuwe notitie mag direct; geen extra bewaren/openen.
        const titleField=document.getElementById('noteEditorTitle');
        const editor=document.getElementById('noteRichEditor');
        const now=Date.now();
        note=normalizeNote({
          id:uid('note'),createdAt:now,updatedAt:now,
          title:titleField.value.trim()||(file.name||'Bijlage').replace(/\.[^.]+$/,'')||'Bijlage',
          html:editor.innerHTML.trim(),plainText:(editor.innerText||'').trim(),
          folderId:document.getElementById('noteEditorFolder').value||null,
          pinned:document.getElementById('noteEditorPinned').checked,
          files:[attachment]
        });
        data.notes.push(note);
        if(!saveData()){
          data.notes=data.notes.filter(n=>n.id!==note.id);
          toast('Opslaan mislukt. Het bestand is niet toegevoegd.');
          return;
        }
        editingId=note.id;
        titleField.value=note.title;
        document.getElementById('noteEditorHeading').textContent='Notitie bewerken';
        document.getElementById('deleteRichNoteButton').style.display='block';
        document.getElementById('noteEditorActions').classList.add('has-delete');
      }else{
        if(!data.notes.includes(note))return;
        const previousTime=note.updatedAt;
        note.files||=[];
        note.files.push(attachment);
        note.updatedAt=Date.now();
        if(!saveData()){
          note.files.pop();
          note.updatedAt=previousTime;
          toast('Opslag vol: bestand niet toegevoegd. Je bestaande notitie blijft bewaard.');
          return;
        }
      }
      committed=true;
      renderFileList(note);renderNotes();toast('Bestand toegevoegd aan de notitie.');
      }finally{
        if(persistedBlob&&!committed){
          try{await window.MijnDagNoteBlobStorage.remove(attachment.id);}catch{}
        }
      }
    }catch(error){toast(error?.message||'Bestand toevoegen is mislukt.');}
    finally{
      addingNoteFile=false;
      buttons.forEach(button=>{if(button)button.disabled=false;});
      for(const id of ['noteFileInput','noteCameraInput']){
        const input=document.getElementById(id);
        if(input)input.value='';
      }
    }
  }
  async function addQuickFile(file,folderId,kind){
    if(!file||addingQuickFile)return;
    addingQuickFile=true;
    const validFolder=folderId && data.folders.some(f=>f.id===folderId)?folderId:null;
    let attachment=null,persistedBlob=false,committed=false;
    try{
      attachment=await prepareNoteFile(file);
      if((kind==='photo'||kind==='camera')&&!attachment.mime.startsWith('image/'))
        throw new Error('Kies een foto uit Drive, geen PDF of ander document.');
      if(kind==='pdf' && attachment.mime.startsWith('image/'))
        throw new Error('Kies bij Document een PDF of Word-bestand.');
      if(attachment.storage==='indexeddb'){
        await window.MijnDagNoteBlobStorage.put(attachment.id,attachment.blob);
        delete attachment.blob;
        persistedBlob=true;
      }
      const now=Date.now();
      const note=normalizeNote({
        id:uid('note'),createdAt:now,updatedAt:now,
        title:(file.name||'Bijlage').replace(/\.[^.]+$/,'')||'Bijlage',
        html:'',plainText:'',folderId:validFolder,pinned:false,files:[attachment]
      });
      data.notes.push(note);
      if(!saveData()){
        data.notes=data.notes.filter(n=>n.id!==note.id);
        throw new Error('Opslaan mislukt. Er is niets toegevoegd.');
      }
      committed=true;
      renderNotes();
      toast('Toegevoegd aan je notitieboek.');
    }catch(e){toast(e?.message||'Bestand toevoegen is mislukt.');}
    finally{
      if(persistedBlob&&!committed)try{await window.MijnDagNoteBlobStorage.remove(attachment.id);}catch{}
      addingQuickFile=false;
    }
  }

  function runQuickAction(action){
    toggleCreateMenu(false);
    if(action==='write'){openEditor();return;}
    if(action==='checklist'){openEditor(null,{checklist:true});return;}
    const inputId=action==='camera'?'noteQuickCameraInput':
      action==='photo'?'noteQuickPhotoInput':
      action==='pdf'?'noteQuickPdfInput':null;
    if(!inputId)return;
    // De gekozen map onthouden voordat Android de camera/bestandskiezer opent.
    quickPickerFolder=activeFolder!==ALL&&activeFolder!==GENERAL?activeFolder:null;
    quickPickerType=action;
    const input=document.getElementById(inputId);
    if(input){input.value='';input.click();}
  }

  async function openNoteFile(id,noteId=editingId){
    const note=data.notes.find(n=>n.id===noteId),file=note?.files?.find(f=>f.id===id);
    if(!file)return;
    if(isDocumentMime(file.mime)){
      try{
        let src=file.src;
        if(file.storage==='indexeddb'){
          const blob=await window.MijnDagNoteBlobStorage.get(file.id);
          if(!blob || blob.size!==file.size)throw new Error('Document ontbreekt. Controleer je back-up.');
          src=await readFileAsDataURL(blob);
        }
        const base64=src.split(',')[1];
        if(!base64)throw new Error('Document kon niet worden gelezen.');
        if(window.AndroidWidgetBridge){
          const native=window.AndroidWidgetBridge;
          if(isWordMime(file.mime)){
            if(!native.beginNoteDocument||!native.addNoteDocumentPart||!native.finishNoteDocument)
              throw new Error('Word-bestanden openen vereist de volgende Mijn dag-update.');
            if(!native.beginNoteDocument(base64.length))throw new Error('Document openen mislukt.');
            let ok=true;
            for(let i=0;ok&&i<base64.length;i+=32768)ok=native.addNoteDocumentPart(base64.slice(i,i+32768));
            if(!ok || !native.finishNoteDocument(file.name,file.mime))throw new Error('Document openen mislukt.');
          }else if(file.storage==='indexeddb'){
            if(!native.beginNotePdf||!native.addNotePdfPart||!native.finishNotePdf)
              throw new Error('Deze Mijn dag-versie kan grotere PDF\'s nog niet openen.');
            if(!native.beginNotePdf(base64.length))throw new Error('PDF openen mislukt.');
            let ok=true;
            for(let i=0;ok&&i<base64.length;i+=32768)ok=native.addNotePdfPart(base64.slice(i,i+32768));
            if(!ok||!native.finishNotePdf(file.name))throw new Error('PDF openen mislukt.');
          }else if(typeof native.openNotePdf==='function')native.openNotePdf(base64,file.name);
          else throw new Error('PDF bekijken vereist een bijgewerkte Android-app.');
          return;
        }
        const bytes=Uint8Array.from(atob(base64),c=>c.charCodeAt(0));
        const url=URL.createObjectURL(new Blob([bytes],{type:file.mime}));
        const a=document.createElement('a');a.href=url;
        if(isWordMime(file.mime))a.download=file.name;
        else{a.target='_blank';a.rel='noopener';}
        document.body.appendChild(a);a.click();a.remove();
        setTimeout(()=>URL.revokeObjectURL(url),120000);
      }catch(e){toast(e?.message||'Document openen lukte niet.');}
      return;
    }
    // Samsung: foto direct openen in Galerij, geen handmatige zoomknoppen.
    const native=window.AndroidWidgetBridge;
    if(native?.beginNoteImage && native?.addNoteImagePart && native?.finishNoteImage){
      try{
        const src=String(file.src||'');
        const comma=src.indexOf(',');
        const mime=src.slice(5,src.indexOf(';'));
        if(comma<0 || !['image/jpeg','image/png','image/webp'].includes(mime) ||
           src.slice(src.indexOf(';'),comma)!==';base64')
          throw new Error('Onbekend fotoformaat.');
        const base64=src.slice(comma+1);
        if(!native.beginNoteImage(base64.length))throw new Error('Foto openen mislukt.');
        let ok=true;
        for(let i=0;ok&&i<base64.length;i+=32768)ok=native.addNoteImagePart(base64.slice(i,i+32768));
        if(!ok||!native.finishNoteImage(mime))throw new Error('Foto openen mislukt.');
        return;
      }catch(error){toast(error?.message||'Foto openen mislukt.');return;}
    }
    // Web en eerdere Android-versies tonen een eenvoudige fullscreen foto.
    imageZoom=1;
    const img=document.getElementById('noteImagePreview');
    img.src=file.src;img.style.width='100%';
    document.getElementById('noteImageViewport').scrollTop=0;
    document.getElementById('noteImageModal').classList.add('open');
  }
  function removeNoteFile(id){
    const note=data.notes.find(n=>n.id===editingId);
    if(!note?.files?.some(f=>f.id===id))return;
    if(!confirm('Dit bestand uit de notitie verwijderen?'))return;
    const original=note.files,updated=original.filter(f=>f.id!==id),prevTime=note.updatedAt;
    note.files=updated;note.updatedAt=Date.now();
    if(!saveData()){note.files=original;note.updatedAt=prevTime;toast('Verwijderen kon niet worden opgeslagen.');return;}
    renderFileList(note);renderNotes();toast('Bijlage verwijderd.');
    if(original.some(f=>f.id===id&&f.storage==='indexeddb')){
      window.MijnDagNoteBlobStorage?.remove(id).catch(()=>{});
    }
  }
  // Geen HTML uitvoeren uit opgeslagen notities: alleen basale opmaak lezen.
  function safeReaderHtml(html,plainText){
    const template=document.createElement('template');
    template.innerHTML=String(html||'');
    const allowed=new Set(['P','DIV','BR','H3','B','STRONG','I','EM','U','S','STRIKE',
      'UL','OL','LI','BLOCKQUOTE','SPAN','FONT']);
    const blocked=new Set(['SCRIPT','STYLE','IFRAME','OBJECT','EMBED','SVG','MATH',
      'FORM','TEMPLATE','NOSCRIPT','VIDEO','AUDIO','LINK','META']);
    function safeNode(node){
      if(node.nodeType===3)return esc(node.textContent||'');
      if(node.nodeType!==1)return '';
      const tag=node.tagName.toUpperCase();
      if(blocked.has(tag))return '';
      const body=Array.from(node.childNodes).map(safeNode).join('');
      if(!allowed.has(tag))return body;
      if(tag==='BR')return '<br>';
      // execCommand(foreColor) maakt soms FONT, kleur uitsluitend als hex.
      if(tag==='FONT'||tag==='SPAN'){
        const attrColor=tag==='FONT' ? node.getAttribute('color') : '';
        const styleColor=tag==='SPAN' ? (node.style?.color||'') : '';
        const color=attrColor||styleColor;
        const valid=/^#[0-9a-f]{3}(?:[0-9a-f]{3})?$/i.test(color) ? color : '';
        return valid ? '<span style="color:'+valid+'">'+body+'</span>' : body;
      }
      const canonical=tag==='STRIKE'?'s':tag.toLowerCase();
      return '<'+canonical+'>'+body+'</'+canonical+'>';
    }
    const markup=Array.from(template.content.childNodes).map(safeNode).join('');
    return markup.trim() || (plainText ? '<p>'+esc(plainText).replace(/\n/g,'<br>')+'</p>' : '');
  }
  function openReader(id){
    const note=data.notes.find(n=>n.id===id);if(!note)return;
    readingNoteId=note.id;
    document.getElementById('noteReaderTitle').textContent=noteTitle(note);
    document.getElementById('noteReaderMeta').textContent=folderName(note.folderId);
    document.getElementById('noteReaderContent').innerHTML=safeReaderHtml(note.html,note.plainText);
    document.getElementById('noteReaderFiles').innerHTML=(note.files||[]).map(file=>{
      const photo=file.mime?.startsWith('image/') && /^data:image\//.test(file.src||'');
      const word=isWordMime(file.mime);
      const icon=photo?'<img src="'+esc(file.src)+'" alt="" />':
        (word?(file.mime===DOCX_MIME?'DOCX':'DOC'):'PDF');
      return '<button type="button" class="note-reader-file" data-note-reader-file="'+esc(file.id)+'">'+
        '<span class="note-reader-file-thumb'+(word?' word':'')+'">'+icon+'</span>'+
        '<span>'+esc(file.name||'Bijlage')+'</span></button>';
    }).join('');
    const scroll=document.getElementById('noteReaderScroll');if(scroll)scroll.scrollTop=0;
    document.getElementById('noteReaderModal').classList.add('open');
  }
  function closeReader(){
    readingNoteId=null;
    document.getElementById('noteReaderModal')?.classList.remove('open');
  }
  function editReaderNote(){
    const id=readingNoteId;
    closeReader();
    if(id)openEditor(id);
  }

  function openEditor(id=null,options={}) {
    replaceNotesView();
    editingId=id;
    const note=id?data.notes.find(n=>n.id===id):null;
    document.getElementById('noteEditorHeading').textContent=note?'Notitie bewerken':'Nieuwe notitie';
    document.getElementById('noteEditorTitle').value=note?.title||'';
    const select=document.getElementById('noteEditorFolder');
    const chosenFolder=note?.folderId||(id?null:(activeFolder!==ALL&&activeFolder!==GENERAL?activeFolder:null));
    select.innerHTML=folderOptions(chosenFolder);
    select.value=chosenFolder||'';
    document.getElementById('noteEditorPinned').checked=Boolean(note?.pinned);
    document.getElementById('noteRichEditor').innerHTML=note?.html||'';
    renderFileList(note);
    document.getElementById('deleteRichNoteButton').style.display=note?'block':'none';
    document.getElementById('noteEditorActions').classList.toggle('has-delete',Boolean(note));
    document.getElementById('noteEditorModal').classList.add('open');
    const sheet=document.querySelector('#noteEditorModal .note-editor-sheet');
    if(sheet)sheet.scrollTop=0;
    if(options.checklist){
      document.getElementById('noteRichEditor').textContent='☐ ';
    }
    if(options.focus!==false)setTimeout(()=>{
      const field=document.getElementById(options.checklist?'noteRichEditor':(note?'noteRichEditor':'noteEditorTitle'));
      try{field?.focus({preventScroll:true});}catch{field?.focus();}
      if(sheet)sheet.scrollTop=0;
    },80);
  }
  function closeEditor() {
    editingId=null;
    document.getElementById('noteEditorModal')?.classList.remove('open');
  }
  function saveEditor() {
    const title=document.getElementById('noteEditorTitle').value.trim();
    const editor=document.getElementById('noteRichEditor');
    const plain=(editor.innerText||'').trim();
    if(!title&&!plain&&!(editingId&&data.notes.find(n=>n.id===editingId)?.files?.length)){toast('Schrijf eerst iets in je notitie.');return;}
    const now=Date.now();
    const existing=editingId?data.notes.find(n=>n.id===editingId):null;
    const previous=existing?{...existing}:null;
    const note=existing||{id:uid('note'),createdAt:now};
    note.title=title;
    note.html=editor.innerHTML.trim();
    note.plainText=plain;
    note.folderId=document.getElementById('noteEditorFolder').value||null;
    note.pinned=document.getElementById('noteEditorPinned').checked;
    note.updatedAt=now;
    if(!existing)data.notes.push(note);
    if(!saveData()){
      if(existing)Object.assign(existing,previous);
      else data.notes=data.notes.filter(n=>n.id!==note.id);
      toast('Opslaan mislukt. De notitie staat nog open; probeer opnieuw.');
      return;
    }
    closeEditor(); renderNotes(); toast(existing?'Notitie bijgewerkt.':'Notitie bewaard.');
  }
  function openNoteAction(id) {
    const note=data.notes.find(n=>n.id===id);if(!note)return;
    actionNoteId=id;
    document.getElementById('noteActionHeading').textContent=noteTitle(note);
    document.getElementById('noteActionPin').textContent=note.pinned?'Losmaken':'Vastzetten';
    document.getElementById('noteActionModal').classList.add('open');
  }
  function closeNoteAction() {
    actionNoteId=null;
    document.getElementById('noteActionModal')?.classList.remove('open');
  }
  function deleteNote(id) {
    const note=data.notes.find(n=>n.id===id);if(!note)return;
    if(!confirm('“'+noteTitle(note)+'” verwijderen?'))return;
    const next={...data,notes:data.notes.filter(n=>n.id!==id)};
    if(!saveData(next)){toast('Verwijderen is niet opgeslagen. Probeer het opnieuw.');return;}
    data=next;
    // Na succesvolle verwijdering mogen ook de bijbehorende binaire
    // bestanden weg. Bij mislukken van bewaren blijft alles behouden.
    for(const file of note.files||[]){
      if(file.storage==='indexeddb'){
        window.MijnDagNoteBlobStorage?.remove(file.id).catch(()=>{});
      }
    }
    if(editingId===id)closeEditor();
    if(readingNoteId===id)closeReader();
    if(actionNoteId===id)closeNoteAction();
    renderNotes();
    toast('Notitie definitief verwijderd.');
  }
  function deleteEditorNote() {
    if(editingId)deleteNote(editingId);
  }

  function updateNotebookEditPreview(folder){
    const preview=document.getElementById('noteBookEditPreview');
    if(!preview || !folder)return;
    preview.innerHTML=folder.cover ?
      `<img src="${esc(folder.cover)}" alt="Omslag van ${esc(folder.name)}" />` :
      '<span>Standaard</span>';
    const reset=document.getElementById('noteBookResetCover');
    if(reset)reset.disabled=!folder.cover;
  }
  function openNotebookEdit(id){
    const folder=data.folders.find(f=>f.id===id);
    if(!folder)return;
    editingNotebookId=id;
    const inp=document.getElementById('noteBookEditName');
    if(inp)inp.value=folder.name;
    updateNotebookEditPreview(folder);
    document.getElementById('noteBookEditModal')?.classList.add('open');
  }
  function closeNotebookEdit(){
    editingNotebookId=null;
    choosingNotebookCoverId=null;
    document.getElementById('noteBookEditModal')?.classList.remove('open');
  }
  function renameNotebook(){
    const folder=data.folders.find(f=>f.id===editingNotebookId);
    if(!folder)return;
    const name=String(document.getElementById('noteBookEditName')?.value||'').trim();
    if(!name || name.length>70){toast('Geef je notitieboek een naam van maximaal 70 tekens.');return;}
    if(data.folders.some(f=>f.id!==folder.id && f.name.toLocaleLowerCase('nl')===name.toLocaleLowerCase('nl'))){
      toast('Er bestaat al een notitieboek met die naam.');return;
    }
    const previous=folder.name;
    folder.name=name;
    if(!saveData()){
      folder.name=previous;
      toast('Naam wijzigen lukte niet. Je notitieboek is onveranderd.');return;
    }
    closeNotebookEdit();renderNotes();renderFolderManager();toast('Naam gewijzigd.');
  }
  function resetNotebookCover(){
    const folder=data.folders.find(f=>f.id===editingNotebookId);
    if(!folder)return;
    if(!folder.cover)return;
    const old=folder.cover;
    folder.cover='';
    if(!saveData()){
      folder.cover=old;
      toast('Standaardomslag kon niet worden opgeslagen.');return;
    }
    updateNotebookEditPreview(folder);renderNotes();toast('Standaardomslag hersteld.');
  }
  async function prepareNotebookCover(file){
    if(!file)throw new Error('Geen foto gekozen.');
    if(!file.size || file.size>30*1024*1024)
      throw new Error('Kies een foto kleiner dan 30 MB.');
    const signature=new Uint8Array(await file.slice(0,12).arrayBuffer());
    const jpeg=signature[0]===255&&signature[1]===216&&signature[2]===255;
    const png=signature[0]===137&&String.fromCharCode(...signature.slice(1,4))==='PNG';
    const webp=String.fromCharCode(...signature.slice(0,4))==='RIFF'&&
       String.fromCharCode(...signature.slice(8,12))==='WEBP';
    const gif=String.fromCharCode(...signature.slice(0,3))==='GIF';
    const hint=String(file.type||'').startsWith('image/') ||
       /\.(jpe?g|png|webp|gif|heic|heif|avif)$/i.test(file.name||'');
    if(!(jpeg||png||webp||gif||hint))
      throw new Error('Kies een foto (JPG, PNG of een andere afbeelding).');
    const url=URL.createObjectURL(file);
    try{
      const img=new Image();
      await new Promise((resolve,reject)=>{
        img.onload=resolve;
        img.onerror=()=>reject(new Error('Deze foto kan niet geopend worden. Probeer JPG of PNG.'));
        img.src=url;
      });
      if(!img.naturalWidth||!img.naturalHeight)throw new Error('Ongeldige foto.');
      const canvas=document.createElement('canvas');
      const ctx=canvas.getContext('2d');
      if(!ctx)throw new Error('Foto verwerken lukt niet op dit toestel.');
      for(const width of [420,340,280,220]){
        canvas.width=width;canvas.height=Math.round(width/.88);
        const ratio=Math.max(canvas.width/img.naturalWidth,canvas.height/img.naturalHeight);
        const w=img.naturalWidth*ratio,h=img.naturalHeight*ratio;
        ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);
        ctx.drawImage(img,(canvas.width-w)/2,(canvas.height-h)/2,w,h);
        for(const quality of [.77,.62,.46,.34]){
          const cover=canvas.toDataURL('image/jpeg',quality);
          if(/^data:image\/jpeg;base64,/.test(cover)&&cover.length<=105000)return cover;
        }
      }
      throw new Error('De foto is te groot voor een omslag. Kies een kleinere foto.');
    }finally{URL.revokeObjectURL(url);}
  }
  async function saveNotebookCover(file,id){
    if(!file||savingNotebookCover)return;
    savingNotebookCover=true;
    const choose=document.getElementById('noteBookChooseCover');
    if(choose)choose.disabled=true;
    try{
      const cover=await prepareNotebookCover(file);
      const folder=data.folders.find(f=>f.id===id);
      if(!folder){toast('Dit notitieboek bestaat niet meer.');return;}
      const previous=folder.cover||'';
      folder.cover=cover;
      if(!saveData()){
        folder.cover=previous;
        toast('Opslag vol. De bestaande omslag blijft behouden.');return;
      }
      if(editingNotebookId===id)updateNotebookEditPreview(folder);
      renderNotes();
      toast('Foto als omslag ingesteld.');
    }catch(error){toast(error?.message||'Foto instellen is mislukt.');}
    finally{
      savingNotebookCover=false;
      if(choose)choose.disabled=false;
      const input=document.getElementById('noteBookCoverInput');
      if(input)input.value='';
    }
  }
  function deleteNotebook(){
    const folder=data.folders.find(f=>f.id===editingNotebookId);
    if(!folder)return;
    if(!confirm('Notitieboek “'+folder.name+'” verwijderen? Alle notities blijven bewaard bij Losse notities.'))return;
    const beforeNotes=data.notes.map(n=>n.folderId);
    const beforeFolders=data.folders;
    data.notes.forEach(n=>{if(n.folderId===folder.id)n.folderId=null;});
    data.folders=data.folders.filter(f=>f.id!==folder.id);
    if(!saveData()){
      data.folders=beforeFolders;
      data.notes.forEach((n,i)=>{n.folderId=beforeNotes[i];});
      toast('Notitieboek verwijderen lukte niet. Alles is behouden.');return;
    }
    if(activeFolder===folder.id){activeFolder=ALL;booksHome=true;}
    closeNotebookEdit();renderFolderManager();renderNotes();
    toast('Notitieboek verwijderd. De notities staan bij Losse notities.');
  }

  function openFolders() {
    replaceNotesView();
    renderFolderManager();
    document.getElementById('noteFoldersModal').classList.add('open');
    // Beheren toont eerst het overzicht; geen ongevraagd toetsenbord.
  }
  function closeFolders() {
    document.getElementById('noteFoldersModal')?.classList.remove('open');
  }
  function renderFolderManager() {
    const list=document.getElementById('folderManageList'); if(!list)return;
    list.innerHTML=data.folders.length?data.folders.map(f=>`
      <div class="folder-manage-row">
        <strong>${esc(f.name)}</strong>
        <button type="button" data-notebook-edit="${esc(f.id)}">Bewerken</button>
      </div>
    `).join(''):`<div class="empty-state">Je hebt nog geen eigen notitieboeken.</div>`;
  }
  function createFolder(name) {
    name=String(name||'').trim();
    if(!name){toast('Geef de map een naam.');return null;}
    if(data.folders.some(f=>f.name.toLowerCase()===name.toLowerCase())){toast('Die map bestaat al.');return null;}
    const folder={id:uid('folder'),name,createdAt:Date.now()};
    data.folders.push(folder);
    if(!saveData()){data.folders.pop();toast('Notitieboek kon niet worden opgeslagen.');return null;}
    renderFolderManager();renderFolderStrip();return folder;
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
    const book=e.target.closest('[data-notebook-open]');
    if(book){openNotebook(book.dataset.notebookOpen);return;}
    const editBook=e.target.closest('[data-notebook-edit]');
    if(editBook){openNotebookEdit(editBook.dataset.notebookEdit);return;}
    if(e.target.closest('#notesBackToBooks')){backToNotebooks();return;}
    if(e.target.closest('#notesBooksSearchButton')){
      openNotebook(ALL);
      document.getElementById('notesSearchInput')?.focus();
      return;
    }
    const filter=e.target.closest('[data-note-folder-filter]');
    if(filter){openNotebook(filter.dataset.noteFolderFilter);return;}

    if(e.target.closest('#notesSearchClear')){
      searchQuery='';
      const input=document.getElementById('notesSearchInput');
      if(input){input.value='';input.focus();}
      renderNotes();return;
    }
    if(e.target.closest('#noteCreateFab')){
      const fab=document.getElementById('noteCreateFab');
      toggleCreateMenu(fab?.getAttribute('aria-expanded')!=='true');
      return;
    }
    if(e.target.closest('#noteCreateBackdrop')){toggleCreateMenu(false);return;}
    const quick=e.target.closest('[data-note-quick]');
    if(quick){runQuickAction(quick.dataset.noteQuick);return;}
    if(e.target.closest('#newRichNoteButton')){openEditor();return;}
    if(e.target.closest('#quickNewFolderButton')){
      openFolders();
      setTimeout(()=>document.getElementById('newFolderName')?.focus(),60);
      return;
    }
    if(e.target.closest('#openFoldersButton')){openFolders();return;}
    if(e.target.closest('#closeFoldersModal')){closeFolders();return;}
    if(e.target.closest('#closeNoteBookEdit')){closeNotebookEdit();return;}
    if(e.target.closest('#noteBookSaveName')){renameNotebook();return;}
    if(e.target.closest('#noteBookResetCover')){resetNotebookCover();return;}
    if(e.target.closest('#noteBookDelete')){deleteNotebook();return;}
    if(e.target.closest('#noteBookChooseCover')){
      const folder=data.folders.find(f=>f.id===editingNotebookId);
      if(folder&&!savingNotebookCover){
        choosingNotebookCoverId=folder.id;
        const input=document.getElementById('noteBookCoverInput');
        if(input){input.value='';input.click();}
      }
      return;
    }
    if(e.target.closest('#closeNoteReader')){closeReader();return;}
    if(e.target.closest('#editNoteReader')){editReaderNote();return;}
    const readerFile=e.target.closest('[data-note-reader-file]');
    if(readerFile&&readingNoteId){
      void openNoteFile(readerFile.dataset.noteReaderFile,readingNoteId);
      return;
    }
    if(e.target.closest('#closeNoteEditor')){closeEditor();return;}
    if(e.target.closest('#saveRichNoteButton')){saveEditor();return;}
    if(e.target.closest('#noteAddFileButton')){document.getElementById('noteFileInput')?.click();return;}
    if(e.target.closest('#noteCameraButton')){document.getElementById('noteCameraInput')?.click();return;}
    const fileOpen=e.target.closest('[data-note-file-open]');
    if(fileOpen){openNoteFile(fileOpen.dataset.noteFileOpen);return;}
    const fileRemove=e.target.closest('[data-note-file-remove]');
    if(fileRemove){removeNoteFile(fileRemove.dataset.noteFileRemove);return;}
    if(e.target.closest('#closeNoteImage')||e.target.id==='noteImageModal'){document.getElementById('noteImageModal').classList.remove('open');return;}
    if(e.target.closest('#noteZoomOut,#noteZoomIn')){
      imageZoom=Math.min(4,Math.max(1,imageZoom+(e.target.closest('#noteZoomIn')?.id==='noteZoomIn'?.5:-.5)));
      document.getElementById('noteImagePreview').style.width=Math.round(imageZoom*100)+'%';
      return;
    }
    if(e.target.closest('#deleteRichNoteButton')){deleteEditorNote();return;}
    const action=e.target.closest('[data-rich-note-action]');
    if(action){openNoteAction(action.dataset.richNoteAction);return;}
    if(e.target.closest('#closeNoteAction')){closeNoteAction();return;}
    if(e.target.closest('#noteActionEdit')){
      const id=actionNoteId;closeNoteAction();if(id)openEditor(id);
      return;
    }
    if(e.target.closest('#noteActionPin')){
      const note=data.notes.find(n=>n.id===actionNoteId);
      if(note){
        note.pinned=!note.pinned;note.updatedAt=Date.now();
        if(!saveData()){note.pinned=!note.pinned;toast('Niet opgeslagen. Probeer opnieuw.');return;}
        closeNoteAction();renderNotes();toast(note.pinned?'Notitie vastgezet.':'Notitie losgemaakt.');
      }
      return;
    }
    if(e.target.closest('#noteActionDelete')){if(actionNoteId)deleteNote(actionNoteId);return;}

    const open=e.target.closest('[data-rich-note-open]');
    if(open){
      const note=data.notes.find(n=>n.id===open.dataset.richNoteOpen);
      if(!note)return;
      const attachment=standaloneAttachment(note);
      if(attachment){void openNoteFile(attachment.id,note.id);}
      else openReader(note.id);
      return;
    }
    const pin=e.target.closest('[data-rich-note-pin]');
    if(pin){const n=data.notes.find(x=>x.id===pin.dataset.richNotePin);if(n){const prev=n.pinned;n.pinned=!n.pinned;n.updatedAt=Date.now();if(!saveData()){n.pinned=prev;toast('Niet opgeslagen. Probeer opnieuw.');return;}renderNotes();}return;}

    const heading=e.target.closest('[data-note-block]');
    if(heading){execCommand('formatBlock',heading.dataset.noteBlock);return;}
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
  });

  document.addEventListener('keydown',e=>{
    if(e.key==='Escape' && document.getElementById('noteCreateFab')?.getAttribute('aria-expanded')==='true'){
      toggleCreateMenu(false);return;
    }
    if(e.key==='Escape' && readingNoteId){closeReader();return;}
    if(e.key==='Escape' && editingNotebookId){closeNotebookEdit();return;}
    if(e.key==='Escape' && actionNoteId){closeNoteAction();return;}
    if(e.target?.id==='newFolderName'&&e.key==='Enter'){e.preventDefault();document.getElementById('createFolderButton')?.click();}
    if(e.target?.id==='noteBookEditName'&&e.key==='Enter'){e.preventDefault();renameNotebook();}
  });
  document.addEventListener('click',e=>{
    if(e.target?.id==='noteActionModal')closeNoteAction();
    if(e.target?.id==='noteBookEditModal')closeNotebookEdit();
  });

  document.addEventListener('input',e=>{
    if(e.target?.id==='notesSearchInput'){
      searchQuery=String(e.target.value||'');
      renderNotes();
    }
  });
  document.addEventListener('change',e=>{
    if(e.target?.id==='noteBookCoverInput'){
      const chosen=e.target.files?.[0],folder=choosingNotebookCoverId;
      if(chosen&&folder)void saveNotebookCover(chosen,folder);
      choosingNotebookCoverId=null;
      e.target.value='';
      return;
    }
    if(e.target?.id==='noteFileInput'||e.target?.id==='noteCameraInput')addNoteFile(e.target.files?.[0]);
    if(['noteQuickCameraInput','noteQuickPhotoInput','noteQuickPdfInput'].includes(e.target?.id)){
      const file=e.target.files?.[0],folder=quickPickerFolder,kind=quickPickerType;
      if(file)void addQuickFile(file,folder,kind);
      e.target.value='';
    }
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
      const note=normalizeNote({id:uid('note'),title:'',body:text,folderId:null,pinned:false,createdAt:now,updatedAt:now});
      data.notes.push(note);
      if(!saveData()){
        data.notes=data.notes.filter(n=>n.id!==note.id);
        toast('Notitie kon niet worden opgeslagen.');
        return false;
      }
      renderNotes();return true;
    }
  };
})();
