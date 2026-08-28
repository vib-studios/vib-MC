package net.vibmc.entity;

import net.vibmc.inventory.Inventory;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import io.netty.channel.Channel;
import net.vibmc.network.ProtocolState;
import net.vibmc.network.handler.PacketHandler;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.TimeUnit;
import net.vibmc.player.GameMode;
import net.vibmc.world.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.vibmc.server.VibMC;

public class ServerPlayer extends Entity {
    private final User user;
    private PacketHandler handler;
    private ProtocolState protocolState=ProtocolState.HANDSHAKE;
    private String username,virtualHost,forwardedAddress;
    private final Inventory inventory;
    private final Set<Long> sentChunks = ConcurrentHashMap.newKeySet();
    private GameMode gameMode;
    private boolean flying;
    private boolean allowFlight;
    private int heldItemSlot;
    private int foodLevel;
    private float foodSaturation;
    private int loadedChunkX;
    private int loadedChunkZ;
    private int streamedViewDistance = 1;
    private boolean chunkStreamingFailed;
    private int floatingTicks;
    private boolean floatingCandidate;
    private int movementGraceTicks = 100;
    private double lastClientY;
    private boolean hasClientPosition;
    private int portalTicks;
    private int portalCooldown;
    private float fallDistance;
    private int voidDamageCooldown;
    private int nextTeleportId;
    private int pendingTeleportId=-1;
    private UUID cameraTargetUuid;
    private final PlayerVitals vitals = new PlayerVitals(this);
    private final ItemStack[] armor = new ItemStack[net.vibmc.inventory.Armor.SLOTS];
    private ItemStack offhand = ItemStack.EMPTY;
    private int hurtCooldown;
    private DamageSource lastDamageSource = DamageSource.GENERIC;
    private String lastAttacker;
    private double lastExhaustionX, lastExhaustionZ;
    private final net.vibmc.crafting.CraftingGrid crafting = new net.vibmc.crafting.CraftingGrid(2);
    private net.vibmc.inventory.WindowSession openWindow;
    private ItemStack carried = ItemStack.EMPTY;
    private int nextWindowId = 1;
    private boolean sneaking, sprinting;

    public ServerPlayer(User user) {
        this(null,user,null,null);
    }

    public ServerPlayer(World world, User user, String username, UUID uuid) {
        super(world, uuid);
        this.user = user;
        this.username = username;
        this.inventory = new Inventory("Inventory", 36);
        this.gameMode = GameMode.SURVIVAL;
        this.heldItemSlot = 0;
        this.foodLevel = 20;
        this.foodSaturation = 5.0f;
        this.x = 8.5;
        this.y = 0;
        this.z = 8.5;
        java.util.Arrays.fill(armor, ItemStack.EMPTY);
    }

    public Channel channel(){return (Channel)user.getChannel();}
    public boolean isOpen(){return channel().isActive();}
    public void setHandler(PacketHandler handler){this.handler=handler;}
    public PacketHandler getHandler(){return handler;}
    public ProtocolState protocolState(){return protocolState;}
    /** Mirrors protocol state for vib-MC core logic; PacketEvents owns its User state machine. */
    public void setProtocolState(ProtocolState state){protocolState=state;}
    public void setUsername(String value){username=value;user.getProfile().setName(value);}
    public UUID getProfileUuid(){return uuid;}public void setProfileUuid(UUID value){uuid=value;user.getProfile().setUUID(value);}
    public java.util.List<com.github.retrooper.packetevents.protocol.player.TextureProperty> getProfileProperties(){return user.getProfile().getTextureProperties();}
    public void setProfileProperties(java.util.List<com.github.retrooper.packetevents.protocol.player.TextureProperty> value){user.getProfile().setTextureProperties(new java.util.ArrayList<>(value));}
    public String getVirtualHost(){return virtualHost;}public void setVirtualHost(String value){virtualHost=value;}
    public String getForwardedAddress(){return forwardedAddress;}public void setForwardedAddress(String value){forwardedAddress=value;}
    public void setWorldAndIdentity(World world,String username,UUID uuid){this.world=world;setUsername(username);setProfileUuid(uuid);}
    public void enableEncryption(byte[] secret){try{SecretKeySpec key=new SecretKeySpec(secret,"AES");IvParameterSpec iv=new IvParameterSpec(secret);Cipher decrypt=Cipher.getInstance("AES/CFB8/NoPadding");decrypt.init(Cipher.DECRYPT_MODE,key,iv);Cipher encrypt=Cipher.getInstance("AES/CFB8/NoPadding");encrypt.init(Cipher.ENCRYPT_MODE,key,iv);Runnable install=()->{if(channel().pipeline().get("minecraft_decrypt")==null){channel().pipeline().addFirst("minecraft_encrypt",new net.vibmc.network.packetevents.codec.MinecraftCipherEncoder(encrypt));channel().pipeline().addFirst("minecraft_decrypt",new net.vibmc.network.packetevents.codec.MinecraftCipherDecoder(decrypt));}};if(channel().eventLoop().inEventLoop())install.run();else channel().eventLoop().execute(install);}catch(Exception e){throw new IllegalStateException("Could not enable encryption",e);}}
    public void disconnect(String reason){if(!isOpen())return;net.kyori.adventure.text.Component component=net.kyori.adventure.text.Component.text(reason);if(protocolState==ProtocolState.LOGIN)send(new com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect(component));else if(protocolState==ProtocolState.CONFIGURATION)send(new com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect(component));else if(protocolState==ProtocolState.PLAY)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect(component));else{forceClose();return;}channel().eventLoop().schedule(()->channel().close(),50,TimeUnit.MILLISECONDS);}
    public void forceClose(){if(channel().isOpen())channel().close();}

    public void spawnAtSpawn() {
        int[] spawn;
        int[] structureSpawn = net.vibmc.world.structure.StructureRegistry.spawnPoint(getWorld());
        if (structureSpawn != null) spawn = getWorld().findDrySpawn(structureSpawn[0],structureSpawn[1],24);
        else spawn = getWorld().findDrySpawn(8, 8, 64);
        this.x = spawn[0] + 0.5;
        this.z = spawn[1] + 0.5;
        this.y = getWorld().findSafeSpawnY(spawn[0], spawn[1]);
        ensureSafePosition();
        this.onGround = true;
    }

    /** Restores authoritative state; PlayerManager owns the required Respawn packet sequence. */
    public void respawn() {
        revive();
        setHealth(getMaxHealth());
        setFoodLevel(20);
        setFoodSaturation(5.0f);
        fallDistance = 0.0f;
        hurtCooldown = 0;
        vitals.reset();
        sendEntityFlags();
        spawnAtSpawn();
    }

    public void kill(){
        if(!alive)return;
        lastDamageSource=DamageSource.GENERIC;
        lastAttacker=null;
        setHealth(0.0f);
        die();
        sendHealth();
    }

    public boolean hurt(float amount, DamageSource source){
        return hurt(amount, source, null);
    }

    /**
     * The single entry point for taking damage. Applies armour, invulnerability frames, the
     * hurt flash and sound, and records what to say if this is the killing blow. Creative and
     * spectator players are never hurt.
     */
    public boolean hurt(float amount, DamageSource source, String attacker){
        if(!alive||!isInWorld()||source==null||!Float.isFinite(amount)||amount<=0)return false;
        if(gameMode==GameMode.CREATIVE||gameMode==GameMode.SPECTATOR||isInvulnerable())return false;
        if(source.usesInvulnerabilityFrames()){
            if(hurtCooldown>0)return false;
            hurtCooldown=10;
        }
        float applied=source.reducedByArmor()
                ?net.vibmc.inventory.Armor.reduce(amount,net.vibmc.inventory.Armor.totalPoints(armor))
                :amount;
        lastDamageSource=source;
        lastAttacker=attacker;
        damage(applied);
        sendHealth();
        net.vibmc.world.Effects.status(this,net.vibmc.world.Effects.STATUS_HURT);
        if(source.hurtSound()!=null)net.vibmc.world.Effects.sound(world,x,y,z,source.hurtSound(),
                com.github.retrooper.packetevents.protocol.sound.SoundCategory.PLAYER,1.0f,1.0f);
        return true;
    }

    /**
     * Death effects and the death message. The inventory is deliberately kept: vib-MC has no
     * item entities, so dropping it would destroy it with no way to get it back.
     */
    @Override
    protected void onDeath(){
        if(!isInWorld())return;
        net.vibmc.world.Effects.status(this,net.vibmc.world.Effects.STATUS_DEATH);
        net.vibmc.world.Effects.sound(world,x,y,z,
                com.github.retrooper.packetevents.protocol.sound.Sounds.ENTITY_PLAYER_DEATH,
                com.github.retrooper.packetevents.protocol.sound.SoundCategory.PLAYER,1.0f,1.0f);
        VibMC server=VibMC.getInstance();
        if(server==null||username==null)return;
        String message=lastDamageSource.deathMessage(username,lastAttacker);
        server.getPlayerManager().broadcastMessage(net.vibmc.network.JsonText.component(message));
        server.getLogger().info("%s", message);
    }

    public boolean isSneaking(){return sneaking;}
    public void setSneaking(boolean value){sneaking=value;}
    public boolean isSprinting(){return sprinting;}
    public void setSprinting(boolean value){sprinting=value;}
    public boolean isBurning(){return vitals.isBurning();}
    public void setOnFire(int ticks){vitals.setOnFire(ticks);}
    public void addExhaustion(float amount){vitals.addExhaustion(amount);}

    public ItemStack getArmorPiece(int slot){
        return slot<0||slot>=armor.length?ItemStack.EMPTY:armor[slot].copy();
    }

    public void setArmorPiece(int slot,ItemStack piece){
        if(slot<0||slot>=armor.length)return;
        armor[slot]=piece==null?ItemStack.EMPTY:piece.copy();
        broadcastEquipment();
    }

    public ItemStack getOffhandItem(){return offhand.copy();}

    public void setOffhandItem(ItemStack item){
        offhand=item==null?ItemStack.EMPTY:item.copy();
        broadcastEquipment();
    }

    public void broadcastEquipment(){
        VibMC server=VibMC.getInstance();
        if(server!=null&&isInWorld())server.getPlayerManager().broadcastEquipment(this);
    }

    public void sendEntityFlags(){
        VibMC server=VibMC.getInstance();
        if(server!=null&&isInWorld())server.getPlayerManager().broadcastEntityFlags(this);
    }

    /** Air-bubble metadata; index 1 has been the air supply since 1.9. */
    public void sendAirSupply(int air){
        if(user==null)return;
        send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata(
                getEntityId(),java.util.Collections.singletonList(
                        new com.github.retrooper.packetevents.protocol.entity.data.EntityData<Integer>(
                                1,com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.INT,air))));
    }

    /** Relocates only invalid positions: inside blocks, fluids, or without a solid floor. */
    public void ensureSafePosition(){
        if(!isInWorld()||isSafeStandingPosition(x,y,z))return;
        int originX=(int)Math.floor(x),originZ=(int)Math.floor(z);
        int surfaceY=world.findSafeSpawnY(originX,originZ);
        if(isSafeStandingPosition(originX+0.5,surfaceY,originZ+0.5)){
            setPosition(originX+0.5,surfaceY,originZ+0.5);return;
        }
        int[] dry=world.findDrySpawn(originX,originZ,32);
        double safeY=world.findSafeSpawnY(dry[0],dry[1]);
        if(!isSafeStandingPosition(dry[0]+0.5,safeY,dry[1]+0.5)){
            dry=world.findDrySpawn(8,8,96);safeY=world.findSafeSpawnY(dry[0],dry[1]);
        }
        // Nothing dry anywhere the search could reach: build land rather than spawn in water.
        if(!isSafeStandingPosition(dry[0]+0.5,safeY,dry[1]+0.5))safeY=world.createDryPlatform(dry[0],dry[1]);
        setPosition(dry[0]+0.5,safeY,dry[1]+0.5);
    }

    /** True when the block at the player's feet or head is water. */
    public boolean isSubmerged(){
        if(!isInWorld())return false;
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);
        if(by<0||by>=255)return false;
        return net.vibmc.world.Blocks.same(world.getBlockAt(bx,by,bz),net.vibmc.world.Blocks.WATER)
                ||net.vibmc.world.Blocks.same(world.getBlockAt(bx,by+1,bz),net.vibmc.world.Blocks.WATER);
    }

    private boolean isSafeStandingPosition(double testX,double testY,double testZ){
        int bx=(int)Math.floor(testX),by=(int)Math.floor(testY),bz=(int)Math.floor(testZ);
        if(by<1||by>=255)return false;
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState feet=world.getBlockAt(bx,by,bz);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState head=world.getBlockAt(bx,by+1,bz);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState floor=world.getBlockAt(bx,by-1,bz);
        return net.vibmc.world.Blocks.same(feet,net.vibmc.world.Blocks.AIR)
                &&net.vibmc.world.Blocks.same(head,net.vibmc.world.Blocks.AIR)
                &&!net.vibmc.world.Blocks.same(floor,net.vibmc.world.Blocks.AIR)
                &&!net.vibmc.world.Blocks.same(floor,net.vibmc.world.Blocks.WATER)
                &&!net.vibmc.world.Blocks.same(floor,net.vibmc.world.Blocks.LAVA);
    }

    @Override
    public void tick() {
        if (!alive || !isInWorld()) return;
        if (movementGraceTicks > 0) movementGraceTicks--;
        if (portalCooldown > 0) portalCooldown--;
        if (voidDamageCooldown > 0) voidDamageCooldown--;
        if (hurtCooldown > 0) hurtCooldown--;
        if (y < -64 && voidDamageCooldown == 0 && gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR) {
            hurt(4.0f, DamageSource.VOID);
            voidDamageCooldown = 10;
        }
        vitals.tick();
        // Re-checked every tick, not just when a movement packet arrives: the flag is sticky
        // and a creative player who stops sending positions (dimension change, unconfirmed
        // teleport) must never be kicked for a survival-era flag.
        if (floatingCandidate && !isFloatingCheckExempt()) {
            floatingTicks++;
            if (floatingTicks > 80 && user != null) {
                floatingTicks = 0;
                floatingCandidate = false;
                disconnect("Flying is not enabled on this server");
            }
        } else {
            floatingCandidate = false;
            floatingTicks = 0;
        }
        tickPortal();
        // Do not run fallback gravity during login/teleport grace. The client may not have
        // decoded its destination chunks yet and can otherwise be advanced into terrain.
        if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR
                && movementGraceTicks == 0 && !onGround && !flying) {
            double below = getWorld().getHighestBlockY((int) Math.floor(x), (int) Math.floor(z)) + 1;
            if (y > below) {
                y -= 0.1;
            } else {
                y = below;
                onGround = true;
            }
        }
    }

    private void tickPortal() {
        VibMC server = VibMC.getInstance();
        if (server == null || portalCooldown > 0) return;
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState feet = world.getChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16))
                .getBlock(Math.floorMod(blockX, 16), blockY, Math.floorMod(blockZ, 16));
        com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState below = world.getChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16))
                .getBlock(Math.floorMod(blockX, 16), Math.max(0, blockY - 1), Math.floorMod(blockZ, 16));
        boolean netherPortal = net.vibmc.world.Blocks.same(feet, net.vibmc.world.Blocks.NETHER_PORTAL);
        boolean endPortal = net.vibmc.world.Blocks.same(feet, net.vibmc.world.Blocks.END_PORTAL)
                || net.vibmc.world.Blocks.same(below, net.vibmc.world.Blocks.END_PORTAL);
        if (!netherPortal && !endPortal) {
            portalTicks = 0;
            return;
        }
        portalTicks++;
        int requiredTicks = endPortal ? 1 : 80;
        if (portalTicks < requiredTicks) return;

        net.vibmc.world.World target;
        if (endPortal) {
            target = world.environment() == net.vibmc.world.WorldEnvironment.END
                    ? server.getWorldManager().getMainWorld() : server.getWorldManager().getEndWorld();
        } else {
            target = world.environment() == net.vibmc.world.WorldEnvironment.NETHER
                    ? server.getWorldManager().getMainWorld() : server.getWorldManager().getNetherWorld();
        }
        if (target != null) {
            portalTicks = 0;
            portalCooldown = 100;
            server.getPlayerManager().transferPlayer(this, target);
        }
    }

    public boolean isAwaitingTeleportConfirmation(){return pendingTeleportId>=0;}
    public void confirmTeleport(int teleportId){
        if(teleportId==pendingTeleportId){pendingTeleportId=-1;movementGraceTicks=Math.max(movementGraceTicks,20);}
    }

    /**
     * Anyone allowed to leave the ground is exempt from the floating check: creative and
     * spectator players, anyone the server granted flight, an active movement grace window,
     * and a player whose movement is being ignored while a teleport is unconfirmed.
     */
    boolean isFloatingCheckExempt() {
        return allowFlight || flying || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR
                || movementGraceTicks > 0 || isAwaitingTeleportConfirmation();
    }

    /** Vanilla-style lenient floating check; all other legal movement is accepted. */
    public void handleClientMovement(boolean reportedOnGround) {
        double currentY = getY();
        double groundY = getWorld().getHighestSolidY(
                (int) Math.floor(getX()), (int) Math.floor(getZ())) + 1.0;
        if (isFloatingCheckExempt() || reportedOnGround || currentY <= groundY + 0.5) {
            floatingCandidate = false;
        } else {
            floatingCandidate = hasClientPosition && currentY - lastClientY >= -0.03125;
        }
        if (hasClientPosition && !reportedOnGround && currentY < lastClientY) {
            fallDistance += (float) (lastClientY - currentY);
        } else if (reportedOnGround) {
            if (fallDistance > 3.0f && gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR) {
                hurt(fallDistance - 3.0f, DamageSource.FALL);
            }
            fallDistance = 0.0f;
        }
        lastClientY = currentY;
        hasClientPosition = true;
        accumulateMovementExhaustion();
    }

    /** Walking costs hunger; the vanilla rate is 0.01 exhaustion per block travelled. */
    private void accumulateMovementExhaustion(){
        double dx=x-lastExhaustionX,dz=z-lastExhaustionZ;
        double travelled=Math.sqrt(dx*dx+dz*dz);
        lastExhaustionX=x;
        lastExhaustionZ=z;
        if(travelled>0.01&&travelled<8.0&&onGround)vitals.addExhaustion((float)(travelled*(sprinting?0.1:0.01)));
    }

    /** Public so a plugin that changes health can push it to the client. */
    public void sendHealth() {
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth(getHealth(),foodLevel,foodSaturation));
    }

    public void sendMessage(String message) {
        if(user!=null)user.sendMessage(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(message));
    }

    public void sendKeepAlive(long id) {
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive(id));
    }

    public void teleport(double x, double y, double z) {
        setPosition(x, y, z);
        movementGraceTicks = 60;
        floatingTicks = 0;
        floatingCandidate = false;
        hasClientPosition = false;
        boolean confirmsTeleport=user!=null&&user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_9);
        int teleportId=confirmsTeleport?nextTeleportId++:0;
        if(nextTeleportId<0)nextTeleportId=0;
        pendingTeleportId=confirmsTeleport?teleportId:-1;
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook(x,y,z,yaw,pitch,(byte)0,teleportId,true));
        VibMC server = VibMC.getInstance();
        if (server != null) server.getPlayerManager().broadcastPlayerPosition(this);
    }

    private void send(com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet){user.sendPacket(packet);}

    public boolean isInWorld(){return world!=null;}

    public User getUser() {
        return user;
    }

    public String getUsername() {
        return username;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getGameMode() {
        return gameMode.getId();
    }

    public GameMode getGameModeEnum() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        if (gameMode == null) {
            throw new IllegalArgumentException("gameMode cannot be null");
        }
        if(this.gameMode==GameMode.SPECTATOR&&gameMode!=GameMode.SPECTATOR)resetSpectatorCamera(true);
        this.gameMode = gameMode;
        boolean unrestricted = gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
        setInvulnerable(unrestricted);
        VibMC running = VibMC.getInstance();
        allowFlight = unrestricted || (running != null && running.getConfig().allowFlight());
        if (gameMode == GameMode.SPECTATOR) flying = true;
        else if (!allowFlight) flying = false;
        if (isFloatingCheckExempt()) {
            floatingCandidate = false;
            floatingTicks = 0;
        }
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE,getGameMode()));
        sendAbilities();
        if (running != null) running.getPlayerManager().updateGameModeVisibility(this);
    }

    public void acknowledgeBlockChange(int sequence){
        if(user!=null&&sequence>=0&&user.getClientVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19)){
            send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges(sequence));
        }
    }

    public UUID getCameraTargetUuid(){return cameraTargetUuid;}

    public void setSpectatorCamera(ServerPlayer target){
        if(gameMode!=GameMode.SPECTATOR||target==null||target==this||target.getWorld()!=getWorld())return;
        cameraTargetUuid=target.getUuid();
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera(target.getEntityId()));
    }

    public void resetSpectatorCamera(boolean moveToTarget){
        if(cameraTargetUuid==null)return;
        ServerPlayer target=null;VibMC server=VibMC.getInstance();
        if(server!=null)target=server.getPlayerManager().getPlayer(cameraTargetUuid);
        cameraTargetUuid=null;
        if(moveToTarget&&target!=null&&target.getWorld()==getWorld())
            setPositionAndRotation(target.getX(),target.getY(),target.getZ(),target.getYaw(),target.getPitch());
        if(user!=null){
            send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera(getEntityId()));
            if(moveToTarget&&target!=null&&target.getWorld()==getWorld())teleport(x,y,z);
        }
    }

    private void sendAbilities() {
        if(user!=null)send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities(isInvulnerable(),flying,allowFlight,gameMode==GameMode.CREATIVE,.05f,.1f));
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    public void setAllowFlight(boolean allowFlight) {
        this.allowFlight = allowFlight;
    }

    public int getHeldItemSlot() {
        return heldItemSlot;
    }

    public void setHeldItemSlot(int heldItemSlot) {
        if (heldItemSlot < 0 || heldItemSlot > 8) {
            throw new IllegalArgumentException("held item slot must be between 0 and 8");
        }
        this.heldItemSlot = heldItemSlot;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = Math.max(0, Math.min(20, foodLevel));
    }

    public float getFoodSaturation() {
        return foodSaturation;
    }

    public void setFoodSaturation(float foodSaturation) {
        if (!Float.isFinite(foodSaturation)) {
            throw new IllegalArgumentException("food saturation must be finite");
        }
        this.foodSaturation = Math.max(0.0f, Math.min(foodLevel, foodSaturation));
    }

    public net.vibmc.player.storage.PlayerData snapshotPersistentState() {
        if (!isInWorld() || uuid == null) throw new IllegalStateException("player is not fully initialized");
        ItemStack[] worn = new ItemStack[armor.length];
        for (int slot = 0; slot < armor.length; slot++) worn[slot] = armor[slot];
        return new net.vibmc.player.storage.PlayerData(world.name(), x, y, z, yaw, pitch,
                health, foodLevel, foodSaturation, gameMode, flying, heldItemSlot,
                inventory.getSlots(), worn, offhand, vitals.airSupply(), vitals.exhaustion());
    }

    public void restorePersistentState(net.vibmc.player.storage.PlayerData data, World restoredWorld) {
        if (data == null || restoredWorld == null) throw new IllegalArgumentException("restored state and world are required");
        world = restoredWorld;
        setPositionAndRotation(data.x, data.y, data.z, data.yaw, data.pitch);
        health = data.health <= 0.0f ? maxHealth : Math.min(maxHealth, data.health);
        alive = true;
        foodLevel = Math.max(0, Math.min(20, data.foodLevel));
        foodSaturation = Math.max(0.0f, Math.min(foodLevel, data.foodSaturation));
        gameMode = data.gameMode;
        boolean unrestricted = gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
        invulnerable = unrestricted;
        allowFlight = unrestricted || VibMC.getInstance().getConfig().allowFlight();
        flying = allowFlight && data.flying;
        heldItemSlot = Math.max(0, Math.min(8, data.heldItemSlot));
        inventory.clear();
        int length = Math.min(inventory.getSize(), data.inventory.length);
        for (int slot = 0; slot < length; slot++) inventory.setSlot(slot, data.inventory[slot]);
        java.util.Arrays.fill(armor, ItemStack.EMPTY);
        int worn = Math.min(armor.length, data.armor.length);
        for (int slot = 0; slot < worn; slot++) armor[slot] = data.armor[slot].copy();
        offhand = data.offhand.copy();
        vitals.reset();
        vitals.setAirSupply(data.airSupply);
        vitals.setExhaustion(data.exhaustion);
        onGround = false;
        movementGraceTicks = 100;
        // A stored position inside water would drown the player on login; creative and
        // spectator players keep whatever position they logged out at.
        if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR && isSubmerged()) ensureSafePosition();
        resetChunkStreaming();
    }

    public void addItem(ItemStack item) {
        inventory.addItem(item);
        sendInventory();
    }

    public void sendInventory() {
        if(user==null)return;
        java.util.List<ItemStack> items=new java.util.ArrayList<>();
        for(int slot=0;slot<46;slot++)items.add(windowSlot(slot));
        send(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems(0,0,items,ItemStack.EMPTY));
    }

    /**
     * Maps a vanilla window-0 slot onto vib-MC storage: 0 is the crafting result, 1-4 the 2x2
     * grid, 5-8 armour, 9-35 the main inventory, 36-44 the hotbar, and 45 the offhand.
     */
    public ItemStack windowSlot(int slot){
        if(slot>=36&&slot<=44)return inventory.getSlot(slot-36);
        if(slot>=9&&slot<=35)return inventory.getSlot(slot);
        if(slot>=5&&slot<=8)return getArmorPiece(slot-5);
        if(slot==45)return getOffhandItem();
        if(slot>=1&&slot<=4)return crafting.getSlot(slot-1);
        if(slot==0)return crafting.getResult();
        return ItemStack.EMPTY;
    }

    /** Writes a vanilla window-0 slot back into vib-MC storage. */
    public void setWindowSlot(int slot,ItemStack item){
        if(slot>=36&&slot<=44)inventory.setSlot(slot-36,item);
        else if(slot>=9&&slot<=35)inventory.setSlot(slot,item);
        else if(slot>=5&&slot<=8)setArmorPiece(slot-5,item);
        else if(slot==45)setOffhandItem(item);
        else if(slot>=1&&slot<=4)crafting.setSlot(slot-1,item);
    }

    /** Allocates the next container window id, cycling through the byte range vanilla uses. */
    public int nextWindowId(){
        nextWindowId=nextWindowId%100+1;
        return nextWindowId;
    }

    public net.vibmc.crafting.CraftingGrid getCrafting(){return crafting;}
    public net.vibmc.inventory.WindowSession getOpenWindow(){return openWindow;}
    public void setOpenWindow(net.vibmc.inventory.WindowSession window){this.openWindow=window;}
    public ItemStack getCarriedItem(){return carried.copy();}
    public void setCarriedItem(ItemStack item){carried=item==null?ItemStack.EMPTY:item.copy();}

    public Set<Long> getSentChunks() {
        return sentChunks;
    }

    public int getLoadedChunkX() {
        return loadedChunkX;
    }

    public int getLoadedChunkZ() {
        return loadedChunkZ;
    }

    public void setLoadedChunk(int x, int z) {
        this.loadedChunkX = x;
        this.loadedChunkZ = z;
    }
    public void resetChunkStreaming() {
        sentChunks.clear();
        streamedViewDistance = 1;
        loadedChunkX = Integer.MIN_VALUE;
        loadedChunkZ = Integer.MIN_VALUE;
    }
    public int getStreamedViewDistance() { return streamedViewDistance; }
    public void advanceStreamedViewDistance(int maximum) { if (streamedViewDistance < maximum) streamedViewDistance++; }
    public boolean hasChunkStreamingFailed() { return chunkStreamingFailed; }
    public boolean markChunkStreamingFailed() { if (chunkStreamingFailed) return false; chunkStreamingFailed=true; return true; }

    @Override
    public boolean isPlayer() {
        return true;
    }
}
