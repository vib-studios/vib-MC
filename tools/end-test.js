'use strict';
const mineflayer=require('mineflayer');const host=process.env.HOST||'127.0.0.1',port=Number.parseInt(process.env.PORT||'25565',10);
const bot=mineflayer.createBot({host,port,username:'EndTestBot',version:'1.12.2'});let respawn=false,chunks=0,done=false;
bot._client.on('packet',(data,meta)=>{if(meta.name==='respawn'&&data.dimension===1){respawn=true;console.log('[PASS] received End respawn');}if(respawn&&meta.name==='map_chunk')chunks++;});
bot.once('spawn',()=>setTimeout(()=>bot.chat('/dimension end'),500));bot.on('error',e=>{console.error(e.stack||e);process.exit(2)});
setTimeout(()=>{if(respawn&&chunks>0){done=true;console.log('[PASS] decoded End chunks:',chunks);bot.quit();setTimeout(()=>process.exit(0),100);}else{console.error('[FAIL] End transition', {respawn,chunks});process.exit(1);}},7000);
