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
  const weekPlan=Array.isArray(s.weekPlan)?s.weekPlan:[];
  const weekMenu=Array.isArray(s.weekMenu)?s.weekMenu:weekPlan.map(x=>x&&x.recipeId).filter(Boolean);
  const pantry=Array.isArray(s.pantry)?s.pantry.map(p=>({
    id:p&&p.id||'',
    name:p&&p.name||'',
    location:p&&p.location||'Voorraadkast',
    type:p&&p.type||'ingredient',
    mealType:p&&p.mealType||'diner',
    servings:Number(p&&p.servings||2),
    active:!(p&&p.active===false)
  })):[];
  const compact={weekPlan,weekMenu,pantry};
  if(s.foodPrefs&&typeof s.foodPrefs==='object')compact.foodPrefs={
    people:Number(s.foodPrefs.people||2),
    selectedDays:Array.isArray(s.foodPrefs.selectedDays)?s.foodPrefs.selectedDays:weekPlan.map(x=>x&&x.date).filter(Boolean),
    mealType:s.foodPrefs.mealType||'diner',
    usePantryMeals:s.foodPrefs.usePantryMeals!==false
  };
  localStorage.setItem(KEY,JSON.stringify(compact));
}catch(e){}
})();
