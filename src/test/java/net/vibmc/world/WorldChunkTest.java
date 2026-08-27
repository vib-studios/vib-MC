package net.vibmc.world;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class WorldChunkTest{
 @Test void storesAndReturnsSemanticBlocks(){World world=new World(1234L,"test");WorldChunk chunk=world.getChunk(0,0);chunk.setBlock(0,200,0,Blocks.STONE);assertEquals(Blocks.STONE,chunk.getBlock(0,200,0));assertTrue(chunk.isDirty());}
 @Test void blockArraysAreDefensiveCopies(){WorldChunk chunk=new World(2L,"test").getChunk(0,0);com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState original=chunk.getBlock(0,0,0);com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState[] copy=chunk.blocks();copy[0]=Blocks.AIR;assertEquals(original,chunk.getBlock(0,0,0));}
}
