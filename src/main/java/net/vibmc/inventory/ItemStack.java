package net.vibmc.inventory;

import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.util.Objects;

/**
 * A stack of items tracked by vib-MC. Replaces PacketEvents' {@code ItemStack}; item wire ids
 * come from the vendored ViaVersion 1.12 mapping, NBT is adventure-nbt.
 */
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack(ItemTypes.AIR, 0, 0, CompoundBinaryTag.empty());

    private ItemType type;
    private int amount;
    private int damageValue;
    private CompoundBinaryTag nbt;

    public ItemStack(ItemType type, int amount, int damageValue, CompoundBinaryTag nbt) {
        this.type = type == null ? ItemTypes.AIR : type;
        this.amount = Math.max(0, amount);
        this.damageValue = damageValue;
        this.nbt = nbt == null ? CompoundBinaryTag.empty() : nbt;
    }

    public ItemStack(ItemType type) {
        this(type, 1, 0, CompoundBinaryTag.empty());
    }

    public ItemStack(ItemType type, int amount) {
        this(type, amount, 0, CompoundBinaryTag.empty());
    }

    /** An item of the named kind with the damage variant the mapping assigns it, e.g. lapis. */
    public static ItemStack of(String name) {
        return of(name, 1);
    }

    public static ItemStack of(String name, int amount) {
        return new ItemStack(ItemType.of(name), amount, net.vibmc.mappings.Mappings.itemDamage(name),
                CompoundBinaryTag.empty());
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type == null ? ItemTypes.AIR : type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public int getDamageValue() {
        return damageValue;
    }

    public void setDamageValue(int damageValue) {
        this.damageValue = Math.max(0, damageValue);
    }

    public CompoundBinaryTag getNBT() {
        return nbt;
    }

    public void setNBT(CompoundBinaryTag nbt) {
        this.nbt = nbt == null ? CompoundBinaryTag.empty() : nbt;
    }

    public int getMaxAmount() {
        return type.getMaxAmount();
    }

    /** Durability this stack's kind can take before breaking; 0 means it never wears out. */
    public int getMaxDamage() {
        return type.getMaxDurability();
    }

    public boolean isEmpty() {
        return type.equals(ItemTypes.AIR) || amount <= 0;
    }

    public boolean isSimilar(ItemStack other) {
        return other != null && type.equals(other.type) && damageValue == other.damageValue
                && Objects.equals(nbt, other.nbt);
    }

    /** The 1.12 protocol item id. */
    public int getId() {
        return type.getId();
    }

    public ItemStack copy() {
        return new ItemStack(type, amount, damageValue, nbt);
    }

    public Builder toBuilder() {
        return builder().type(type).amount(amount).damage(damageValue).nbt(nbt);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent construction matching PacketEvents' {@code ItemStack.builder()} usage. */
    public static final class Builder {
        private ItemType type = ItemTypes.AIR;
        private int amount = 1;
        private int damage;
        private CompoundBinaryTag nbt = CompoundBinaryTag.empty();

        public Builder type(ItemType type) {
            this.type = type;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder damage(int damage) {
            this.damage = damage;
            return this;
        }

        /** Accepted for 1.12 compatibility: the legacy metadata IS the damage value. */
        public Builder legacyData(int legacyData) {
            this.damage = legacyData;
            return this;
        }

        public Builder nbt(CompoundBinaryTag nbt) {
            this.nbt = nbt;
            return this;
        }

        /** Accepted for PacketEvents compatibility; protocol-340 native, value ignored. */
        public Builder version(Object version) {
            return this;
        }

        public ItemStack build() {
            return new ItemStack(type, amount, damage, nbt);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStack)) return false;
        ItemStack other = (ItemStack) o;
        return type.equals(other.type) && damageValue == other.damageValue
                && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, damageValue, nbt);
    }

    @Override
    public String toString() {
        return (isEmpty() ? "air" : type.name() + " x" + amount) + "{" + damageValue + "}";
    }
}