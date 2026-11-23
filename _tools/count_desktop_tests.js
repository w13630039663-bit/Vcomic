const fs=require("fs"),path=require("path");
const dir="desktop/build/test-results/test";
let tot=0,f=0,e=0,s=0,classes=0;
for(const n of fs.readdirSync(dir)){
  if(!n.endsWith(".xml"))continue;
  const t=fs.readFileSync(path.join(dir,n),"utf8");
  const at=t.match(/<testsuite[^>]*>/)[0];
  const g=k=>{const r=at.match(new RegExp(k+'=\"([^\"]+)\"'));return r?Number(r[1]):0;};
  tot+=g("tests");f+=g("failures");e+=g("errors");s+=g("skipped");classes++;
}
console.log(JSON.stringify({tot,f,e,s,classes}));
