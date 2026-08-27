'use strict';
const mc=require('minecraft-protocol');
const host=process.env.HOST||'127.0.0.1',port=Number.parseInt(process.env.PORT||'25565',10);
const alice=mc.createClient({host,port,username:'ChatAlice',version:'1.12.2'});
const bob=mc.createClient({host,port,username:'ChatBob',version:'1.12.2'});
let logins=0,done=false;
function ready(){if(++logins===2)setTimeout(()=>alice.write('chat',{message:'hello from PacketEvents'}),250);}
alice.once('login',ready);bob.once('login',ready);
bob.on('packet',(data,meta)=>{if(meta.name!=='chat')return;const raw=typeof data.message==='string'?data.message:JSON.stringify(data.message);if(raw.includes('hello from PacketEvents')){done=true;console.log('[PASS] chat delivered:',raw);alice.end();bob.end();setTimeout(()=>process.exit(0),100);}});
for(const client of [alice,bob])client.on('error',e=>{console.error(e.message);process.exit(2)});
setTimeout(()=>{if(!done){console.error('[FAIL] chat not received');process.exit(1)}},10000);
