package com.hazebyte.base;

import com.google.common.base.Preconditions;
import com.hazebyte.base.event.ButtonClickEvent;
import com.hazebyte.base.foundation.CloseButton;
import com.hazebyte.base.foundation.NextButton;
import com.hazebyte.base.foundation.PreviousButton;
import com.hazebyte.base.util.Lib;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * An advanced base menu supporting pagination, in-game updates, properties, and state.
 *
 * State:
 * A entity's UUID maps to the page that they are on.
 */
public abstract class Base extends Component implements InventoryHolder {

    protected JavaPlugin plugin;

    /**
     * The size of the inventory. This should be within limitations of
     * one to six rows.
     */
    private final Size size;

    /**
     * By Minecraft/Spigot limitations, this should be less than 32 characters
     * otherwise calling {@link Base#open(HumanEntity)} will throw an exception.
     */
    private final String title;

    /**
     * The stash of pages.
     */
    private final List<Button[]> pages;

    private final List<Button> observers;

    /**
     * Have we called {@link #addDefaultButtons()} to add close, next, previous pages.
     */
    private boolean hasDefaultButtons;

    /**
     * Used for automatic positioning
     */
    private int raw = 0;

    public Base(JavaPlugin plugin, String title, Size size) {
        this(plugin, title, size, true);
    }

    public Base(JavaPlugin plugin, String title, Size size, boolean hasDefaultButtons) {
        Preconditions.checkNotNull(plugin, "Plugin is null");
        Preconditions.checkNotNull(title, "Title is null");
        Preconditions.checkNotNull(size, "Size is null");
        this.plugin = plugin;
        this.size = size;
        this.title = title;
        this.pages = new ArrayList<>();
        this.pages.add(new Button[size.toInt()]);
        this.observers = new ArrayList<>();
        this.hasDefaultButtons = hasDefaultButtons; // Calls #addDefaultButtons on open to perform calculations
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (title == null ? 0 : title.hashCode());
        hash = 31 * hash + (size == null ? 0 : size.hashCode());
        hash = 31 * hash + pages.size();
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Base)) {
            return false;
        }

        Base base = (Base) object;

        if (!(base.getTitle().equals(this.getTitle()))) {
            return false;
        }
        if (!(base.getSize().equals(this.getSize()))) {
            return false;
        }
        if (!(this.pages.size() == base.pages.size())) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Menu{");
        builder.append("title:" + title).append("slots:" + size).append("}");
        return builder.toString();
    }

    /**
     * Returns the title string that will be put upon an inventory.
     *
     * @return the title of the inventory.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the size of the inventory. Inventories should have at minimum one row
     * and at max six rows.
     * @return the size of the inventory.
     */
    public Size getSize() {
        return size;
    }

    @Override
    public Inventory getInventory() {
        return Bukkit.createInventory(this, size.toInt(), title);
    }

    /**
     * Opens the inventory for the entity. This will default to the first page of Base.
     * @param entity the entity to open the inventory for.
     */
    public void open(HumanEntity entity) {
        open(entity, 0);
    }

    /**
     * Opens the inventory for the entity with a specific page number.
     * This caches page numbers for the inventory which may be used to see
     * the page number of individual entities.
     *
     * @param entity            the entity to open the inventory for.
     * @param page              the page to initialize the items.
     */
    public void open(HumanEntity entity, int page) {
        if (!(InventoryListener.getInstance().registered())) {
            InventoryListener.getInstance().register(plugin);
        }
        if (hasDefaultButtons) addDefaultButtons();

        Inventory inventory = Bukkit.createInventory(this, size.toInt(), title);
        this.setProperty(entity.getUniqueId().toString(), page);
        Button[] barr = pages.get(page);
        for (int i = 0; i < barr.length; i++) {
            if (barr[i] != null) inventory.setItem(i, barr[i].getItem());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            entity.openInventory(inventory);
            startTask(entity);
        }, 3);
    }

    /**
     * Automatically adds in previous, next, and close pages
     */
    public void addDefaultButtons() {
        int pages = this.pages.size();
        LinkedList<Integer> enumerated = new LinkedList<>();
        for (int i = 0; i < pages; i++) enumerated.add(i);

        setIcon(enumerated.toArray(new Integer[enumerated.size()]), size.toInt() - Var.FROM_CENTER, new CloseButton());
        if (pages > 0) {
            int first = enumerated.removeFirst();
            setIcon(enumerated.toArray(new Integer[enumerated.size()]), size.toInt() - Var.LEFT_CENTER, new PreviousButton());
            enumerated.addFirst(first);

            int last = enumerated.removeLast();
            setIcon(enumerated.toArray(new Integer[enumerated.size()]), size.toInt() - Var.RIGHT_CENTER, new NextButton());
        }
    }

    /**
     * Opens the next page for the entity. This will reuse the inventory that is already
     * open. Ensure that this is called within a {@link Base} otherwise
     * nothing will happen.
     * @param entity the entity to switch pages.
     */
    public void nextPage(HumanEntity entity) {
        int page = this.getProperty(entity.getUniqueId().toString(), 0) + 1;
        changePage(entity, page);
    }

    /**
     * Opens the previous page for the entity. This will reuse the inventory that is already
     * open. Ensure that this is called within a {@link Base} otherwise
     * nothing will happen.
     * @param entity the entity to switch pages
     */
    public void previousPage(HumanEntity entity) {
        int page = this.getProperty(entity.getUniqueId().toString(), 0) - 1;
        changePage(entity, page);
    }

    public void updatePage(HumanEntity entity) {
        int page = this.getProperty(entity.getUniqueId().toString(), 0);
        changePage(entity, page);
    }

    public void changePage(HumanEntity entity, int page) {
        Inventory inventory = entity.getOpenInventory().getTopInventory();

        if (inventory.getHolder() instanceof Base
                && (page < pages.size() && page >= 0)) {
            // In any case that items were added, we need to update the default navigation buttons
            if (hasDefaultButtons) this.addDefaultButtons();

            this.setProperty(entity.getUniqueId().toString(), page);
            Button[] barr = pages.get(page);
            for (int i = 0; i < barr.length; i++) {
                if (barr[i] != null) inventory.setItem(i, barr[i].getItem());
                else inventory.setItem(i, null);
            }
        }
    }

    /**
     * Closes the {@link Base} for the entity. This will check that the inventory matches
     * the call.
     * @param entity
     * @return true if the inventory should be closed, false otherwise
     */
    public boolean close(HumanEntity entity) {
        if (entity.getOpenInventory().getTopInventory().getHolder() instanceof Base) {
            Base origin = (Base) entity.getOpenInventory().getTopInventory().getHolder();
            if (this.equals(origin)) {
                Bukkit.getScheduler().runTaskLater(plugin, entity::closeInventory, 1);
                return true;
            }
        }
        return false;
    }

    private void startTask(final HumanEntity entity) {

        new BukkitRunnable() {
            @Override
            public void run() {
                Inventory inventory = entity.getOpenInventory().getTopInventory();
                if (inventory.getHolder() instanceof Base && inventory.getViewers().size() > 0) {
                    inventory.getViewers().forEach(Base.this::update);
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /**
     * See {@link #setIcon(int, int, Button)}
     */
    public Button setIcon(int slot, Button button) {
        return setIcon(0, slot, button);
    }

    public void moveIcon(int page, int slot, int newPage, int newSlot) {
        if (page == newPage && slot == newSlot) return;
        if (page > pages.size()) throw new IndexOutOfBoundsException();
        while (newPage > pages.size()) {
            pages.add(new Button[size.toInt()]);
        }
        Button[] old = pages.get(page);
        Button[] newer = pages.get(newPage);

        newer[newSlot] = old[slot];
        old[slot] = null;
    }

    /**
     * Sets the icon for the Base. The slot should be within the size limit.
     * The button may be null.
     *
     * @param page   the page to set the icon for
     * @param slot   the slot at which this should appear
     * @param button see {@link Button}
     * @return the old button that was at the page and slot
     */
    public Button setIcon(int page, int slot, Button button) {
        while (page >= pages.size()) {
            pages.add(new Button[size.toInt()]);
        }
        Button[] barr = pages.get(page);
        Button old = barr[slot];
        barr[slot] = button;
        return old;
    }

    public List<Button> setIcon(Integer[] pages, int slot, Button button) {
        int[] copy = new int[pages.length];
        for (int i = 0; i < copy.length; i++) copy[i] = pages[i];
        return this.setIcon(copy, slot, button);
    }

    public List<Button> setIcon(int[] pages, int slot, Button button) {
        List<Button> buttons = new ArrayList<>();
        Button tmp;
        for (int page : pages) {
            if ((tmp = setIcon(page, slot, button)) != null)
                buttons.add(tmp);
        }
        return buttons;
    }

    public void addIcon(Button button) {
        addIcon(button, true);
    }

    /**
     * Automatically adds a button to the inventory without considering page or slots.
     *
     * @param button
     * @param override replace the button if true, finds empty space if false.
     */
    public void addIcon(Button button, boolean override) {
        final int USABLE = this.size.toInt() - (hasDefaultButtons ? Var.ITEMS_PER_ROW : 0);
        int page = raw / USABLE;

        if (!override)
            while (getIcon(page, raw % USABLE).isPresent())
                raw++;

        setIcon(page, raw % USABLE, button);
        raw++;
    }

    /**
     * Used for {@link #addIcon(Button)} for positional use.
     *
     * @return raw
     */
    public int getRaw() {
        return this.raw;
    }

    public void attach(Button button) {
        observers.add(button);
    }

    /**
     * Returns the button at the page and slot.
     * @param page
     * @param slot
     * @return {@link Button}
     */
    public Optional<Button> getIcon(int page, int slot) {
        if (page < pages.size() && is(slot)) {
            return Optional.ofNullable(pages.get(page)[slot]);
        }
        return Optional.empty();
    }

    /**
     * Called when the inventory is clicked. This will call broadcast a ButtonClickEvent and
     * run any actions if the button is valid.
     *
     * @param event
     */
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getRawSlot() < 0) return;
        int slot = event.getRawSlot();
        int page = this.getProperty(event.getWhoClicked().getUniqueId().toString(), 0);
        getIcon(page, slot).ifPresent((button) -> {
            ButtonClickEvent bce = new ButtonClickEvent(
                    this,
                    button,
                    event.getWhoClicked(),
                    event.getAction(),
                    event.getClick(),
                    event.getSlot(),
                    event.getRawSlot(),
                    event.getClickedInventory()
            );
            Bukkit.getPluginManager().callEvent(bce);
            if (!bce.isCancelled()) {
                button.run(event.getClick().name(), bce); // Run the action specific to the click
                button.runDefault(bce); // Run the default action
            }
        });
    }

    /**
     * Override this to specify what you want the inventory to do
     * on every tick.
     *
     * @param entity
     */
    public void update(HumanEntity entity) {
        Lib.debug("Calling Update for " + entity.getName());
    }

    /**
     * Called when setState is triggered. This should handle
     * and update all items that contain the same key.
     */
    @Override
    protected void onUpdate(HumanEntity entity, String key) {
        for (Button button : observers) {
            button.setProperty("menu", this);
            button.onUpdate(entity, key);
        }
        updatePage(entity);
    }

    /**
     * Checks if the slot is in the range of a base inventory.
     *
     * @param rawSlot
     * @return true if the slot is in the range of a base inventory, false otherwise
     */
    public boolean is(int rawSlot) {
        return rawSlot < size.toInt();
    }

    public void shiftRowLeft(int startPage, int startPos, int endPage, int endPos) {
        int currentPage = startPage;
        int currentPos = startPos;
        while (currentPage < endPage + 1 && currentPos < endPos) {
            if ((currentPage + 1) < pages.size() && currentPos != 0 && currentPos % 8 == 0) {
                moveIcon(currentPage + 1, 0, currentPage, currentPos);
                if (pages.get(currentPage).length == 0) pages.remove(currentPage);
                currentPage++;
                currentPos = 0;
            } else {
                moveIcon(currentPage, currentPos + 1, currentPage, currentPos);
                currentPos++;
            }
        }
    }

    public void shiftRowRight(int startPage, int startPos, int endPage, int endPos) {
        int currentPage = endPage;
        int currentPos = endPos;
        while (currentPage >= endPage && currentPos > endPos) {
            if ((currentPage - 1) >= 0 && currentPos == 0) {
                moveIcon(currentPage - 1, 8, currentPage, currentPos);
                currentPage--;
                currentPos = 8;
            } else {
                moveIcon(currentPage, currentPos, currentPage, currentPos + 1);
                currentPos--;
            }
        }
    }
}
