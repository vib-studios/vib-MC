'use strict';
const mc = require('minecraft-protocol');
const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '25565', 10);
const results = [];
let center;
let dimensions = [];
let finished = false;

function check(name, ok, detail) {
  results.push(Boolean(ok));
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}: ${detail}`);
}
function wait(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
function varInt(b, c) { let v=0; for(let i=0;i<5;i++){const x=b[c.i++];v|=(x&127)<<(7*i);if(!(x&128))return v>>>0;} throw Error('varint'); }
function long(b,c){let v=0n;for(let i=0;i<8;i++)v=(v<<8n)|BigInt(b[c.i++]);return v;}
function decode(data) {
  const b=Buffer.from(data.chunkData), c={i:0}, sections=new Map();
  for(let s=0;s<16;s++) if(data.bitMap&(1<<s)) {
    const bits=b[c.i++], palette=[]; const pn=varInt(b,c); for(let i=0;i<pn;i++)palette.push(varInt(b,c));
    const n=varInt(b,c), values=[]; for(let i=0;i<n;i++)values.push(long(b,c)); c.i+=4096;
    sections.set(s,{bits,palette,values});
  }
  return (x,y,z) => { const q=sections.get(y>>4); if(!q)return 0; const i=((y&15)<<8)|(z<<4)|x, bi=i*q.bits, li=bi>>6,o=bi&63;
    let v=q.values[li]>>BigInt(o); if(o+q.bits>64)v|=q.values[li+1]<<BigInt(64-o);
    const pi=Number(v&((1n<<BigInt(q.bits))-1n)); return q.palette.length?q.palette[pi]:pi; };
}
function find(get,state){for(let y=0;y<256;y++)for(let z=0;z<16;z++)for(let x=0;x<16;x++)if(get(x,y,z)===state)return{x:x+.5,y,z:z+.5};return null;}

const client=mc.createClient({host,port,username:'DimensionBot',version:'1.12.2'});
client.on('packet',(d,m)=>{
  if(m.name==='map_chunk'&&d.x===0&&d.z===0&&dimensions.length===0) center=decode(d);
  if(m.name==='respawn') dimensions.push(d.dimension);
});
client.on('error',e=>finish(2,e.message));
client.once('login',()=>setTimeout(run,1500));

async function run(){
  const nether=find(center,90<<4), end=find(center,119<<4);
  check('Nether portals are not generated automatically',!nether,JSON.stringify(nether));
  check('End portals are not generated automatically',!end,JSON.stringify(end));
  client.end();
  finish();
}
function finish(code,msg){if(finished)return;finished=true;if(msg)console.error(msg);const f=results.filter(x=>!x).length;console.log(`\n${results.length-f}/${results.length} checks passed`);setTimeout(()=>process.exit(code===undefined?(f?1:0):code),100);}
setTimeout(()=>finish(2,'TIMEOUT'),8000);
