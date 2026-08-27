'use strict';
const mc=require('minecraft-protocol');const host=process.env.HOST||'127.0.0.1',port=Number.parseInt(process.env.PORT||'25565',10);
const client=mc.createClient({host,port,username:'EndTestBot',version:'1.12.2'});let kicked=false;
client.once('login',()=>setTimeout(()=>client.write('chat',{message:'/kick EndTestBot integration kick'}),300));
client.on('packet',(data,meta)=>{if(meta.name==='kick_disconnect'){kicked=true;const reason=String(data.reason);console.log('[PASS] kick packet:',reason);process.exit(reason.includes('integration kick')?0:1);}});
client.on('error',()=>{});setTimeout(()=>{if(!kicked){console.error('[FAIL] player was not kicked');process.exit(2)}},7000);
