package net.vibmc.inventory;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;

import java.util.Objects;

/** Server-owned inventory using PacketEvents' semantic item stacks directly. */
public final class Inventory {
    private final ItemStack[] slots;
    private final String title;

    public Inventory(String title,int size){if(size<=0)throw new IllegalArgumentException("size must be positive");this.title=Objects.requireNonNull(title,"title");slots=new ItemStack[size];clear();}
    public ItemStack getSlot(int index){check(index);return slots[index].copy();}
    public void setSlot(int index,ItemStack item){check(index);slots[index]=item==null?ItemStack.EMPTY:item.copy();}
    public int addItem(ItemStack item){if(item==null||item.isEmpty())return 0;int remaining=item.getAmount();for(ItemStack existing:slots){if(remaining==0)break;if(similar(existing,item)){int space=existing.getType().getMaxAmount()-existing.getAmount();int add=Math.min(space,remaining);existing.setAmount(existing.getAmount()+add);remaining-=add;}}for(int i=0;i<slots.length&&remaining>0;i++){if(slots[i].isEmpty()){int add=Math.min(remaining,item.getType().getMaxAmount());ItemStack placed=item.copy();placed.setAmount(add);slots[i]=placed;remaining-=add;}}return remaining;}
    public void removeItem(int index,int amount){check(index);if(amount<=0||slots[index].isEmpty())return;slots[index].setAmount(slots[index].getAmount()-amount);if(slots[index].getAmount()<=0)slots[index]=ItemStack.EMPTY;}
    public boolean hasItem(ItemType type,int amount){return countItem(type)>=amount;}
    public int countItem(ItemType type){int count=0;for(ItemStack stack:slots)if(!stack.isEmpty()&&stack.getType()==type)count+=stack.getAmount();return count;}
    public void clear(){for(int i=0;i<slots.length;i++)slots[i]=ItemStack.EMPTY;}
    public String getTitle(){return title;}public int getSize(){return slots.length;}
    public ItemStack[] getSlots(){ItemStack[] copy=new ItemStack[slots.length];for(int i=0;i<slots.length;i++)copy[i]=slots[i].copy();return copy;}
    private static boolean similar(ItemStack first,ItemStack second){return first!=null&&!first.isEmpty()&&first.getType()==second.getType()&&first.getDamageValue()==second.getDamageValue()&&Objects.equals(first.getNBT(),second.getNBT());}
    private void check(int index){if(index<0||index>=slots.length)throw new IndexOutOfBoundsException("slot "+index+" is outside inventory size "+slots.length);}
}
