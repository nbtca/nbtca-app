import fs from 'node:fs/promises';
const contract=JSON.parse(await fs.readFile(new URL('../docs/card-upstream/contract.json',import.meta.url),'utf8'));
const headers={Accept:'application/vnd.github+json','User-Agent':'nbtca-app-upstream-check'};
if(process.env.GITHUB_TOKEN) headers.Authorization=`Bearer ${process.env.GITHUB_TOKEN}`;
async function api(path) {
 const response=await fetch(`https://api.github.com/repos/${contract.repository}/${path}`,{headers,signal:AbortSignal.timeout(30000)});
 if(!response.ok) throw new Error(`GitHub upstream check failed: HTTP ${response.status}`);
 return response.json();
}
const commit=await api('commits/main');
const tree=await api(`git/trees/${commit.commit.tree.sha}?recursive=1`);
if(tree.truncated) throw new Error('Upstream tree truncated; compatibility cannot be assessed');
const current=new Map(tree.tree.map(item=>[item.path,item.sha]));
const changed=Object.entries(contract.files).filter(([path,sha])=>current.get(path)!==sha).map(([path])=>path);
const text=[`Card upstream: ${commit.sha}`,`Baseline: ${contract.baselineCommit}`,
 changed.length ? 'Maintainer source changed. Review affected contracts and update Android adapter/tests before updating this baseline.' : 'Watched upstream source matches the reviewed baseline.',
 ...changed.map(path=>`- ${path}`),'Source drift check only; device and business-flow tests are still required.'].join('\n');
console.log(text);
if(process.env.GITHUB_STEP_SUMMARY) await fs.appendFile(process.env.GITHUB_STEP_SUMMARY,text+'\n');
if(changed.length) process.exitCode=1;
