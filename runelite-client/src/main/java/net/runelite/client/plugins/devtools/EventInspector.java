package net.runelite.client.plugins.devtools;import com.google.common.collect.HashMultimap;import com.google.common.collect.Multimap;import lombok.extern.slf4j.Slf4j;import net.runelite.api.*;import net.runelite.api.coords.LocalPoint;import net.runelite.api.coords.WorldPoint;import net.runelite.api.events.*;import net.runelite.client.callback.ClientThread;import net.runelite.client.eventbus.EventBus;import net.runelite.client.eventbus.Subscribe;import net.runelite.client.ui.ColorScheme;import net.runelite.client.ui.DynamicGridLayout;import net.runelite.client.ui.FontManager;import org.jetbrains.annotations.NotNull;import javax.inject.Inject;import javax.swing.*;import javax.swing.border.CompoundBorder;import java.awt.*;import java.awt.event.AdjustmentEvent;import java.awt.event.AdjustmentListener;import java.util.*;import java.util.List;/** * @author Kris | 22/10/2021 */@SuppressWarnings("DuplicatedCode")@Slf4jpublic class EventInspector extends DevToolsFrame {    private final static int MAX_LOG_ENTRIES = 10_000;    private static final int VARBITS_ARCHIVE_ID = 14;    private final Client client;    private final EventBus eventBus;    private final ProjectileTracker projectileTracker;    private final JPanel tracker = new JPanel();    private int lastTick = 0;    private final Map<Skill, Integer> cachedExperienceMap = new HashMap<>();    private final List<OverheadTextChanged> overheadChatList = new ArrayList<>();    private final ClientThread clientThread;    private int[] oldVarps = null;    private int[] oldVarps2 = null;    private Multimap<Integer, Integer> varbits;    private final Set<Actor> facedActors = new HashSet<>();    private final Set<Actor> facedDirectionActors = new HashSet<>();    private final JCheckBox projectiles = new JCheckBox("Projectiles", true);    private final JCheckBox spotanims = new JCheckBox("Spotanims", true);    private final JCheckBox sequences = new JCheckBox("Sequences", true);    private final JCheckBox soundEffects = new JCheckBox("Sound effects", true);    private final JCheckBox areaSoundEffects = new JCheckBox("Area Sound Effects", true);    private final JCheckBox say = new JCheckBox("Say", true);    private final JCheckBox experience = new JCheckBox("Experience", true);    private final JCheckBox messages = new JCheckBox("Messages", true);    private final JCheckBox varbitsCheckBox = new JCheckBox("Varbits", true);    private final JCheckBox varpsCheckBox = new JCheckBox("Varps", true);    private final JCheckBox hitsCheckBox = new JCheckBox("Hits", true);    private final JCheckBox interacting = new JCheckBox("Entity facing", true);    private final JCheckBox tileFacing = new JCheckBox("Tile facing", true);    @Inject    EventInspector(Client client, EventBus eventBus, ClientThread clientThread, ProjectileTracker projectileTracker) {        this.client = client;        this.eventBus = eventBus;        this.clientThread = clientThread;        this.projectileTracker = projectileTracker;        setTitle("Event Inspector");        setLayout(new BorderLayout());        tracker.setLayout(new DynamicGridLayout(0, 1, 0, 3));        final JPanel trackerWrapper = new JPanel();        trackerWrapper.setLayout(new BorderLayout());        trackerWrapper.add(tracker, BorderLayout.NORTH);        final JScrollPane trackerScroller = new JScrollPane(trackerWrapper);        trackerScroller.setPreferredSize(new Dimension(1400, 400));        final JScrollBar vertical = trackerScroller.getVerticalScrollBar();        vertical.addAdjustmentListener(new AdjustmentListener() {            int lastMaximum = actualMax();            private int actualMax() {                return vertical.getMaximum() - vertical.getModel().getExtent();            }            @Override            public void adjustmentValueChanged(AdjustmentEvent e) {                if (vertical.getValue() >= lastMaximum) {                    vertical.setValue(actualMax());                }                lastMaximum = actualMax();            }        });        add(trackerScroller, BorderLayout.CENTER);        final JPanel trackerOpts = new JPanel();        trackerOpts.setLayout(new FlowLayout());        trackerOpts.add(projectiles);        trackerOpts.add(spotanims);        trackerOpts.add(sequences);        trackerOpts.add(soundEffects);        trackerOpts.add(areaSoundEffects);        trackerOpts.add(say);        trackerOpts.add(messages);        trackerOpts.add(experience);        trackerOpts.add(varpsCheckBox);        trackerOpts.add(varbitsCheckBox);        trackerOpts.add(hitsCheckBox);        trackerOpts.add(interacting);        trackerOpts.add(tileFacing);        final JButton clearBtn = new JButton("Clear");        clearBtn.addActionListener(e -> {            tracker.removeAll();            tracker.revalidate();        });        trackerOpts.add(clearBtn);        add(trackerOpts, BorderLayout.SOUTH);        pack();    }    private void addLine(String prefix, String text) {        int tick = client.getTickCount();        SwingUtilities.invokeLater(() -> {            if (tick != lastTick) {                lastTick = tick;                JLabel header = new JLabel("Tick " + tick);                header.setFont(FontManager.getRunescapeSmallFont());                header.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR),                        BorderFactory.createEmptyBorder(3, 6, 0, 0)));                tracker.add(header);            }            JPanel labelPanel = new JPanel();            labelPanel.setLayout(new BorderLayout());            JLabel prefixLabel = new JLabel(prefix);            prefixLabel.setToolTipText(prefix);            JLabel textLabel = new JLabel(text);            prefixLabel.setPreferredSize(new Dimension(400, 14));            prefixLabel.setMaximumSize(new Dimension(400, 14));            labelPanel.add(prefixLabel, BorderLayout.WEST);            labelPanel.add(textLabel);            tracker.add(labelPanel);            // Cull very old stuff            while (tracker.getComponentCount() > MAX_LOG_ENTRIES) {                tracker.remove(0);            }            tracker.revalidate();        });    }    @Subscribe    public void onProjectileMoved(ProjectileMoved event) {        if (!projectiles.isSelected()) return;        projectileTracker.submitProjectileMoved(client, event, (earlyProjectileInfo, dynamicProjectileInfo, prefix, text) -> addLine(prefix, text));    }    @Subscribe    public void spotanimChanged(GraphicChanged event) {        if (!spotanims.isSelected()) return;        Actor actor = event.getActor();        if (actor == null) return;        String actorLabel = formatActor(actor);        StringBuilder graphicsLabelBuilder = new StringBuilder();        graphicsLabelBuilder.append("Graphics(");        graphicsLabelBuilder.append("id = ").append(actor.getGraphic() == 65535 ? -1 : actor.getGraphic());        final int delay = actor.getGraphicStartCycle() - client.getGameCycle();        if (delay != 0) graphicsLabelBuilder.append(", delay = ").append(delay);        if (actor.getGraphicHeight() != 0) graphicsLabelBuilder.append(", height = ").append(actor.getGraphicHeight());        graphicsLabelBuilder.append(")");        addLine(actorLabel, graphicsLabelBuilder.toString());    }    @Subscribe    public void sequenceChanged(AnimationFrameIndexChanged event) {        if (!sequences.isSelected()) return;        Actor actor = event.getActor();        if (actor == null || actor.getAnimationFrameIndex() != 0 || actor.getName() == null || isActorPositionUninitialized(actor)) return;        String actorLabel = formatActor(actor);        StringBuilder animationLabelBuilder = new StringBuilder();        animationLabelBuilder.append("Animation(");        animationLabelBuilder.append("id = ").append(actor.getAnimation() == 65535 ? -1 : actor.getAnimation());        if (actor.getAnimationDelay() != 0) animationLabelBuilder.append(", delay = ").append(actor.getAnimationDelay());        animationLabelBuilder.append(")");        addLine(actorLabel, animationLabelBuilder.toString());    }    @Subscribe    public void soundEffectPlayed(SoundEffectPlayed event) {        if (!soundEffects.isSelected()) return;        final int soundId = event.getSoundId();        final int delay = event.getDelay();        final int loops = event.getLoops();        StringBuilder soundEffectBuilder = new StringBuilder();        soundEffectBuilder.append("SoundEffect(");        soundEffectBuilder.append("id = ").append(soundId);        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);        soundEffectBuilder.append(")");        addLine("Local", soundEffectBuilder.toString());    }    @Subscribe    public void areaSoundEffectPlayed(AreaSoundEffectPlayed event) {        if (!areaSoundEffects.isSelected()) return;        /* Animation-driven sounds will always have the source set to non-null, however that information is useless to us so skip it. */        if (event.getSource() != null) return;        final int soundId = event.getSoundId();        final int delay = event.getDelay();        final int loops = event.getLoops();        final int radius = event.getRange();        StringBuilder soundEffectBuilder = new StringBuilder();        soundEffectBuilder.append("SoundEffect(");        soundEffectBuilder.append("id = ").append(soundId);        if (radius != 0) soundEffectBuilder.append(", radius = ").append(radius);        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);        soundEffectBuilder.append(")");        WorldPoint location = WorldPoint.fromLocal(client, LocalPoint.fromScene(event.getSceneX(), event.getSceneY()));        Optional<Player> sourcePlayer = client.getPlayers().stream().filter(player -> player.getWorldLocation().distanceTo(location) == 0).findAny();        Optional<NPC> sourceNpc = client.getNpcs().stream().filter(npc -> npc.getWorldLocation().distanceTo(location) == 0).findAny();        if (sourcePlayer.isPresent() && sourceNpc.isEmpty()) {            addLine(formatActor(sourcePlayer.get()), soundEffectBuilder.toString());        } else if (sourceNpc.isPresent() && sourcePlayer.isEmpty()) {            addLine(formatActor(sourceNpc.get()), soundEffectBuilder.toString());        } else {            addLine("Unknown(" + "x: " + location.getX() + ", y: " + location.getY() + ")", soundEffectBuilder.toString());        }    }    @Subscribe    public void overheadTextChanged(OverheadTextChanged event) {        if (!say.isSelected()) return;        Actor actor = event.getActor();        if (actor == null) return;        overheadChatList.add(event);    }    /**     * Due to the annoying nature of how overhead chat is handled by the client, the only way we can detect if a message was actually server-driven     * or player-driven is to see if another field was changed shortly after. This strictly applies for player public chat, therefore it     * works great as a means to detect overhead chat messages.     */    @Subscribe    public void showPublicPlayerChatChanged(ShowPublicPlayerChatChanged event) {        if (!overheadChatList.isEmpty()) {            OverheadTextChanged element = overheadChatList.get(overheadChatList.size() - 1);            overheadChatList.remove(element);            log.info("Filtered player-driven overhead chat: " + element.getOverheadText());        }    }    @Subscribe    public void experienceChanged(StatChanged event) {        if (!experience.isSelected()) return;        final int previousExperience = cachedExperienceMap.getOrDefault(event.getSkill(), -1);        cachedExperienceMap.put(event.getSkill(), event.getXp());        if (previousExperience == -1) return;        final int experienceDiff = event.getXp() - previousExperience;        if (experienceDiff == 0) return;        addLine("Local", "Experience(skill = " + event.getSkill().getName() + ", xp = " + experienceDiff + ")");    }    @Subscribe    public void chatMessage(ChatMessage event) {        if (!messages.isSelected()) return;        ChatMessageType type = event.getType();        String name = event.getName();        if (name != null && !name.isEmpty()) {            log.info("Prevented chat message from being logged: " + event.getName() + ", " + type + ", " + event.getMessage());            return;        }        addLine("Local", "Message(type = " + type + ", text = \"" + event.getMessage() + "\")");    }    @Subscribe    public void onClientTick(ClientTick event) {        facedActors.clear();        facedDirectionActors.clear();        if (overheadChatList.isEmpty()) return;        for (OverheadTextChanged message : overheadChatList) {            String text = message.getOverheadText();            addLine(formatActor(message.getActor()), "Say(text = \"" + text + "\")");        }        overheadChatList.clear();    }    @Subscribe    public void onVarbitChanged(VarbitChanged varbitChanged) {        int index = varbitChanged.getIndex();        int[] varps = client.getVarps();        boolean isVarbit = false;        for (int i : varbits.get(index)) {            int old = client.getVarbitValue(oldVarps, i);            int newValue = client.getVarbitValue(varps, i);            String name = null;            for (Varbits varbit : Varbits.values()) {                if (varbit.getId() == i) {                    name = varbit.name();                    break;                }            }            if (old != newValue) {                client.setVarbitValue(oldVarps2, i, newValue);                if (varbitsCheckBox.isSelected()) {                    String prefix = name == null ? "Varbit" : ("Varbit \"" + name + "\"");                    addLine(prefix + " (varpId: " + index + ", oldValue: " + old + ")", "Varbit(id = " + i + ", value = " + newValue + ")");                }                isVarbit = true;            }        }        if (isVarbit || !varpsCheckBox.isSelected()) return;        int old = oldVarps2[index];        int newValue = varps[index];        if (old != newValue) {            String name = null;            for (VarPlayer varp : VarPlayer.values()) {                if (varp.getId() == index) {                    name = varp.name();                    break;                }            }            String prefix = name == null ? "Varp" : ("Varp \"" + name + "\"");            addLine(prefix + " (oldValue: " + old + ")", "Varp(id = " + index + ", value = " + newValue + ")");        }        System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);        System.arraycopy(client.getVarps(), 0, oldVarps2, 0, oldVarps2.length);    }    @Subscribe    public void onHitsplatApplied(HitsplatApplied event) {        if (!hitsCheckBox.isSelected()) return;        Actor actor = event.getActor();        if (actor == null || isActorPositionUninitialized(actor)) return;        Hitsplat hitsplat = event.getHitsplat();        addLine(formatActor(actor), "Hit(type = " + hitsplat.getHitsplatType() + ", amount = " + hitsplat.getAmount() + ")");    }    @Subscribe    public void onInteractingChanged(InteractingChanged event) {        if (!interacting.isSelected()) return;        Actor sourceActor = event.getSource();        if (!facedActors.add(sourceActor)) return;        Actor targetActor = event.getTarget();        if (sourceActor == null || isActorPositionUninitialized(sourceActor)) return;        addLine(formatActor(sourceActor), "FaceEntity(" + (targetActor == null ? "N/A" : formatActor(targetActor)) + ")");    }    @Subscribe    public void onFacedDirectionChanged(FacedDirectionChanged event) {        if (!tileFacing.isSelected()) return;        Actor sourceActor = event.getSource();        if (!facedDirectionActors.add(sourceActor)) return;        if (sourceActor == null || isActorPositionUninitialized(sourceActor)) return;        addLine(formatActor(sourceActor), "FaceCoordinate(direction = " + event.getDirection() + ")");    }    /**     * It is possible for some variables to be uninitialized on login, so as an uber cheap fix, let's try-catch validate if the actor is fully initialized.     */    private boolean isActorPositionUninitialized(Actor actor) {        try {            return actor.getWorldLocation() == null;        } catch (NullPointerException ignored) {            return true;        }    }    private String formatActor(@NotNull Actor actor) {        WorldPoint actorWorldLocation = actor.getWorldLocation();        String coordinateString = "x: " + actorWorldLocation.getX() + ", y: " + actorWorldLocation.getY();        if (actor instanceof Player) {            return ("Player(" + (actor.getName() + ", idx: " + ((Player) actor).getPlayerId() + ", " + coordinateString + ")"));        } else if (actor instanceof NPC) {            return ("Npc(" + (actor.getName() + ", idx: " + ((NPC) actor).getIndex() + ", id: " + ((NPC) actor).getId() + ", " + coordinateString + ")"));        }        return ("Unknown(" + coordinateString + ")");    }    @Override    public void open() {        eventBus.register(this);        if (oldVarps == null) {            oldVarps = new int[client.getVarps().length];            oldVarps2 = new int[client.getVarps().length];        }        varbits = HashMultimap.create();        clientThread.invoke(() -> {            IndexDataBase indexVarbits = client.getIndexConfig();            final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);            for (int id : varbitIds) {                VarbitComposition varbit = client.getVarbit(id);                if (varbit != null) {                    varbits.put(varbit.getIndex(), id);                }            }        });        super.open();    }    @Override    public void close() {        super.close();        tracker.removeAll();        eventBus.unregister(this);    }}