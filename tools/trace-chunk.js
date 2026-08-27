const fs = require('fs')
const SmartBuffer = require('smart-buffer').SmartBuffer
const data = fs.readFileSync('real-chunk.bin')
const reader = SmartBuffer.fromBuffer(data)
const varInt = require('prismarine-chunk/src/pc/common/varInt')
const BitArray = require('prismarine-chunk/src/pc/common/BitArray')

for (let y = 0; y < 16; y++) {
  const pos = reader.readOffset
  try {
    const bitsPerBlock = reader.readUInt8()
    if (bitsPerBlock > 8) {
      const marker = varInt.read(reader)
      const dataLen = varInt.read(reader)
      const arr = new BitArray({ bitsPerValue: 13, capacity: 4096 })
      const want = dataLen * 2
      const same = want === arr.data.length
      const dataArr = arr.readBuffer(reader, want)
      console.log(`section ${y}: at=${pos} bits=${bitsPerBlock} marker=${marker} dataLen=${dataLen} (want ${want}w, ctor ${arr.data.length}w, ${same ? 'READ' : 'SKIPPED'}) now=${reader.readOffset}`)
      const bl = new BitArray({ bitsPerValue: 4, capacity: 4096 }).readBuffer(reader)
      const sl = new BitArray({ bitsPerValue: 4, capacity: 4096 }).readBuffer(reader)
      console.log(`   lights ok now=${reader.readOffset}`)
    } else {
      const num = varInt.read(reader)
      for (let i = 0; i < num; i++) varInt.read(reader)
      const dataLen = varInt.read(reader)
      const arr = new BitArray({ bitsPerValue: bitsPerBlock, capacity: 4096 })
      const want = dataLen * 2
      const same = want === arr.data.length
      arr.readBuffer(reader, want)
      console.log(`section ${y}: at=${pos} bits=${bitsPerBlock} palette(${num}) dataLen=${dataLen} (want ${want}w, ctor ${arr.data.length}w, ${same ? 'READ' : 'SKIPPED'}) now=${reader.readOffset}`)
      const bl = new BitArray({ bitsPerValue: 4, capacity: 4096 }).readBuffer(reader)
      const sl = new BitArray({ bitsPerValue: 4, capacity: 4096 }).readBuffer(reader)
      console.log(`   lights ok now=${reader.readOffset}`)
    }
  } catch (e) {
    console.log(`section ${y}: at=${pos} ERROR ${e.message}`)
    break
  }
}
console.log('remaining:', data.length - reader.readOffset)