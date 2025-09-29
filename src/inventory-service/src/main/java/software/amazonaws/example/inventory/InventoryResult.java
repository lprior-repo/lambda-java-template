package software.amazonaws.example.inventory;

/**
 * Result object for inventory check and reservation operations.
 */
public class InventoryResult {
    private final boolean available;
    private final String availabilityStatus;
    private final String reservationId;
    private final Integer reservedQuantity;
    private final Integer stockLevel;
    private final String unavailabilityReason;

    private InventoryResult(boolean available, String availabilityStatus, String reservationId, 
                           Integer reservedQuantity, Integer stockLevel, String unavailabilityReason) {
        this.available = available;
        this.availabilityStatus = availabilityStatus;
        this.reservationId = reservationId;
        this.reservedQuantity = reservedQuantity;
        this.stockLevel = stockLevel;
        this.unavailabilityReason = unavailabilityReason;
    }

    public static InventoryResult available(String reservationId, Integer reservedQuantity, Integer remainingStock) {
        return new InventoryResult(true, "AVAILABLE", reservationId, reservedQuantity, remainingStock, null);
    }

    public static InventoryResult outOfStock(Integer stockLevel, String reason) {
        return new InventoryResult(false, "OUT_OF_STOCK", null, null, stockLevel, reason);
    }

    public static InventoryResult insufficientStock(Integer stockLevel, String reason) {
        return new InventoryResult(false, "INSUFFICIENT_STOCK", null, null, stockLevel, reason);
    }

    public static InventoryResult error(Integer stockLevel, String reason) {
        return new InventoryResult(false, "ERROR", null, null, stockLevel, reason);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public String getReservationId() {
        return reservationId;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public Integer getStockLevel() {
        return stockLevel;
    }

    public String getUnavailabilityReason() {
        return unavailabilityReason;
    }
}