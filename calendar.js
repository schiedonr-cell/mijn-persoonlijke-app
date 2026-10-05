(()=>{
'use strict';
const KEY='mijnPersoonlijkeAppV1';
const BACKUP_KEY='mijnPersoonlijkeAppRecoveryFullBackupV1';
try{
  if(typeof window.AndroidRecovery==='undefined')return;
  const raw=localStorage.getItem(KEY);
  if(!raw)return;
  if(!localStorage.getItem(BACKUP_KEY))localStorage.setItem(BACKUP_KEY,raw);
  const s=JSON.parse(raw);
  const pantry=Array.isArray(s.pantry)?s.pantry.map(p=>[
    p?.id||'',
    p?.name||'',
    p?.location||'Voorraadkast',
    p?.type||'ingredient',
    p?.mealType||'diner',
    Number(p?.servings||2),
    p?.active!==false
  ]):[];
  const weekPlan=Array.isArray(s.weekPlan)?s.weekPlan:[];
  const weekMenu=Array.isArray(s.weekMenu)?s.weekMenu:weekPlan.map(x=>x?.recipeId).filter(Boolean);
  const prefs=s.foodPrefs&&typeof s.foodPrefs==='object'?{
    people:Number(s.foodPrefs.people||2),
    selectedDays:Array.isArray(s.foodPrefs.selectedDays)?s.foodPrefs.selectedDays:weekPlan.map(x=>x?.date).filter(Boolean),
    mealType:s.foodPrefs.mealType||'diner',
    usePantryMeals:s.foodPrefs.usePantryMeals!==false
  }:undefined;
  const compact={weekPlan,weekMenu,pantry};
  if(prefs)compact.foodPrefs=prefs;
  localStorage.setItem(KEY,JSON.stringify(compact));
}catch(e){}
})();
