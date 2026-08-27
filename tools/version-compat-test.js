'use strict';
const mineflayer=require('mineflayer');
const host=process.env.HOST||'127.0.0.1',port=Number.parseInt(process.env.PORT||'25565',10);
const versions=(process.env.VERSIONS||'1.8,1.9.3,1.11.1,1.12.2,1.13,1.14.4,1.15.2,1.16.4,1.17.1').split(',');
function test(index){
  if(index>=versions.length)return process.exit(0);
  const version=versions[index];let chunks=0,finished=false;
  const bot=mineflayer.createBot({host,port,username:'Compat'+index,version});
  const fail=(message,code)=>{if(finished)return;finished=true;console.error(`[FAIL] ${version}: ${message}`);process.exit(code);};
  bot._client.on('packet',(data,meta)=>{if(meta.name==='map_chunk')chunks++;});
  bot.once('spawn',()=>setTimeout(()=>{
    if(chunks===0)return fail('spawned without chunks',1);
    const below=bot.blockAt(bot.entity.position.offset(0,-1,0));
    if(!below||below.name==='air')return fail(`invalid ground block ${below?`${below.name}/${below.stateId}`:'missing'}`,1);
    finished=true;console.log(`[PASS] ${version}: ${chunks} chunks, ground=${below.name}/${below.stateId}`);
    bot.quit();setTimeout(()=>test(index+1),150);
  },1200));
  bot.on('kicked',reason=>fail(`kicked ${reason}`,1));bot.on('error',error=>fail(error.message,2));
  setTimeout(()=>fail(`timeout, chunks=${chunks}`,3),10000);
}
test(0);
