import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SmartParkingSystem {

    public static void main(String[] args) {
        ParkingRepository repository = new InMemoryParkingRepository();
        repository.addSpot(new ParkingSpot("S-1", SpotType.STANDARD, null));
        repository.addSpot(new ParkingSpot("S-2", SpotType.STANDARD, null));
        repository.addSpot(new ParkingSpot("C-1", SpotType.COMPACT, null));
        repository.addSpot(new ParkingSpot("EV-1", SpotType.EV_CHARGER, new ChargingPort("CCS2", 50.0)));

        PricingStrategy standardPricing = new StandardHourlyPricing(
                new BigDecimal("40.00"),
                new BigDecimal("40.00")
        );
        PricingStrategy evPricing = new EvTieredPricing(
                new BigDecimal("30.00"),
                new BigDecimal("18.00")
        );

        ParkingLotService service = new ParkingLotService(repository, standardPricing, evPricing);

        LocalDateTime t0 = LocalDateTime.of(2026, 11, 23, 10, 0);

        Vehicle car = new Car("KA01AB1234");
        Vehicle bike = new Motorcycle("KA05XY9876");
        Vehicle ev = new ElectricVehicle("KA03EV4455", 60.0, 22.0);

        Ticket carTicket = service.checkIn(car, t0);
        Ticket bikeTicket = service.checkIn(bike, t0.plusMinutes(15));
        Ticket evTicket = service.checkIn(ev, t0.plusMinutes(30));

        System.out.println("Issued tickets: " + carTicket.getTicketId() + ", "
                + bikeTicket.getTicketId() + ", " + evTicket.getTicketId());
        System.out.println("Available spots after check-ins: " + service.getAvailableSpotCount());

        Receipt carReceipt = service.checkOut(carTicket.getTicketId(), t0.plusHours(2).plusMinutes(10));
        System.out.println(carReceipt);

        // Simulate 1.5 hours of charging at 22 kW -> 33 kWh
        Receipt evReceipt = service.checkOut(evTicket.getTicketId(), t0.plusHours(2));
        System.out.println(evReceipt);

        // Bike still parked
        System.out.println("Available spots after 2 check-outs: " + service.getAvailableSpotCount());
        service.checkOut(bikeTicket.getTicketId(), t0.plusHours(1));
        System.out.println("Final available spots: " + service.getAvailableSpotCount());
    }
}

// ==========================================
// Enums & Domain Models (Encapsulation)
// ==========================================

enum VehicleType {
    MOTORCYCLE,
    CAR,
    ELECTRIC_VEHICLE
}

enum SpotType {
    MOTORCYCLE,
    COMPACT,
    STANDARD,
    EV_CHARGER
}

abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType vehicleType;

    protected Vehicle(String licensePlate, VehicleType vehicleType) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("License plate cannot be blank");
        }
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = Objects.requireNonNull(vehicleType, "Vehicle type cannot be null");
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public abstract boolean canFitIn(SpotType spotType);
}

class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE);
    }

    @Override
    public boolean canFitIn(SpotType spotType) {
        return spotType == SpotType.MOTORCYCLE
                || spotType == SpotType.COMPACT
                || spotType == SpotType.STANDARD;
    }
}

class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }

    @Override
    public boolean canFitIn(SpotType spotType) {
        return spotType == SpotType.COMPACT || spotType == SpotType.STANDARD;
    }
}

class ElectricVehicle extends Vehicle {
    private final double batteryCapacityKwh;
    private final double requiredKw;

    public ElectricVehicle(String licensePlate, double batteryCapacityKwh, double requiredKw) {
        super(licensePlate, VehicleType.ELECTRIC_VEHICLE);
        if (batteryCapacityKwh <= 0 || requiredKw <= 0) {
            throw new IllegalArgumentException("Battery capacity and required kW must be positive");
        }
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.requiredKw = requiredKw;
    }

    public double getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public double getRequiredKw() {
        return requiredKw;
    }

    @Override
    public boolean canFitIn(SpotType spotType) {
        return spotType == SpotType.EV_CHARGER || spotType == SpotType.STANDARD;
    }
}

class ChargingPort {
    private final String connectorType;
    private final double maxPowerKw;

    public ChargingPort(String connectorType, double maxPowerKw) {
        if (connectorType == null || connectorType.isBlank()) {
            throw new IllegalArgumentException("Connector type cannot be blank");
        }
        if (maxPowerKw <= 0) {
            throw new IllegalArgumentException("Max power must be positive");
        }
        this.connectorType = connectorType.trim();
        this.maxPowerKw = maxPowerKw;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public double getMaxPowerKw() {
        return maxPowerKw;
    }

    public boolean supports(ElectricVehicle ev) {
        return maxPowerKw >= ev.getRequiredKw();
    }
}

class ParkingSpot {
    private final String spotId;
    private final SpotType spotType;
    private final ChargingPort chargingPort; // nullable for non-EV spots
    private Vehicle occupiedBy;

    public ParkingSpot(String spotId, SpotType spotType, ChargingPort chargingPort) {
        if (spotId == null || spotId.isBlank()) {
            throw new IllegalArgumentException("Spot ID cannot be blank");
        }
        this.spotId = spotId.trim();
        this.spotType = Objects.requireNonNull(spotType, "Spot type cannot be null");
        this.chargingPort = chargingPort;
    }

    public String getSpotId() {
        return spotId;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public ChargingPort getChargingPort() {
        return chargingPort;
    }

    public boolean isAvailable() {
        return occupiedBy == null;
    }

    public Vehicle getOccupiedBy() {
        return occupiedBy;
    }

    public void park(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null");
        if (!isAvailable()) {
            throw new IllegalStateException("Spot " + spotId + " is already occupied");
        }
        if (!vehicle.canFitIn(spotType)) {
            throw new IncompatibleSpotException(
                    "Vehicle " + vehicle.getLicensePlate() + " cannot park in " + spotType
            );
        }
        if (spotType == SpotType.EV_CHARGER && vehicle instanceof ElectricVehicle ev) {
            if (chargingPort == null || !chargingPort.supports(ev)) {
                throw new IncompatibleSpotException(
                        "Charger at spot " + spotId + " lacks power for " + ev.getLicensePlate()
                );
            }
        }
        this.occupiedBy = vehicle;
    }

    public void vacate() {
        if (isAvailable()) {
            throw new IllegalStateException("Spot " + spotId + " is already empty");
        }
        this.occupiedBy = null;
    }
}

class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private boolean active;

    public Ticket(Vehicle vehicle, ParkingSpot spot, LocalDateTime entryTime) {
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.vehicle = Objects.requireNonNull(vehicle, "Vehicle cannot be null");
        this.spot = Objects.requireNonNull(spot, "Spot cannot be null");
        this.entryTime = Objects.requireNonNull(entryTime, "Entry time cannot be null");
        this.active = true;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public boolean isActive() {
        return active;
    }

    public void close(LocalDateTime exitTime) {
        Objects.requireNonNull(exitTime, "Exit time cannot be null");
        if (!active) {
            throw new InvalidTicketException("Ticket " + ticketId + " is already closed");
        }
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        this.exitTime = exitTime;
        this.active = false;
    }
}

class Receipt {
    private final String ticketId;
    private final String licensePlate;
    private final String spotId;
    private final long durationMinutes;
    private final BigDecimal amount;

    public Receipt(String ticketId, String licensePlate, String spotId,
                   long durationMinutes, BigDecimal amount) {
        this.ticketId = ticketId;
        this.licensePlate = licensePlate;
        this.spotId = spotId;
        this.durationMinutes = durationMinutes;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Receipt{ticketId='" + ticketId + '\''
                + ", plate='" + licensePlate + '\''
                + ", spot='" + spotId + '\''
                + ", minutes=" + durationMinutes
                + ", amount=" + amount + '}';
    }
}

// ==========================================
// Exceptions
// ==========================================

class NoAvailableSpotException extends RuntimeException {
    public NoAvailableSpotException(String message) {
        super(message);
    }
}

class InvalidTicketException extends RuntimeException {
    public InvalidTicketException(String message) {
        super(message);
    }
}

class IncompatibleSpotException extends RuntimeException {
    public IncompatibleSpotException(String message) {
        super(message);
    }
}

// ==========================================
// Strategy Pattern (Pricing Polymorphism)
// ==========================================

interface PricingStrategy {
    BigDecimal calculateFee(Ticket ticket, LocalDateTime exitTime);
}

class StandardHourlyPricing implements PricingStrategy {
    private final BigDecimal hourlyRate;
    private final BigDecimal minimumFee;

    public StandardHourlyPricing(BigDecimal hourlyRate, BigDecimal minimumFee) {
        if (hourlyRate.signum() <= 0 || minimumFee.signum() < 0) {
            throw new IllegalArgumentException("Hourly rate must be positive and minimum fee non-negative");
        }
        this.hourlyRate = hourlyRate;
        this.minimumFee = minimumFee;
    }

    @Override
    public BigDecimal calculateFee(Ticket ticket, LocalDateTime exitTime) {
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();
        if (minutes < 0) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        long billableHours = Math.max(1L, (minutes + 59L) / 60L);
        BigDecimal fee = hourlyRate.multiply(BigDecimal.valueOf(billableHours));
        return fee.max(minimumFee).setScale(2, RoundingMode.HALF_UP);
    }
}

class EvTieredPricing implements PricingStrategy {
    private final BigDecimal hourlyParkingRate;
    private final BigDecimal ratePerKwh;

    public EvTieredPricing(BigDecimal hourlyParkingRate, BigDecimal ratePerKwh) {
        if (hourlyParkingRate.signum() < 0 || ratePerKwh.signum() < 0) {
            throw new IllegalArgumentException("Rates cannot be negative");
        }
        this.hourlyParkingRate = hourlyParkingRate;
        this.ratePerKwh = ratePerKwh;
    }

    @Override
    public BigDecimal calculateFee(Ticket ticket, LocalDateTime exitTime) {
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();
        if (minutes < 0) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        long billableHours = Math.max(1L, (minutes + 59L) / 60L);
        BigDecimal parkingFee = hourlyParkingRate.multiply(BigDecimal.valueOf(billableHours));

        BigDecimal chargingFee = BigDecimal.ZERO;
        if (ticket.getVehicle() instanceof ElectricVehicle ev
                && ticket.getSpot().getSpotType() == SpotType.EV_CHARGER) {
            double hoursExact = minutes / 60.0;
            double deliveredKw = Math.min(ev.getRequiredKw(), ticket.getSpot().getChargingPort().getMaxPowerKw());
            double energyKwh = Math.min(ev.getBatteryCapacityKwh(), deliveredKw * hoursExact);
            chargingFee = ratePerKwh.multiply(BigDecimal.valueOf(energyKwh));
        }

        return parkingFee.add(chargingFee).setScale(2, RoundingMode.HALF_UP);
    }
}

// ==========================================
// Repository & Service Layer
// ==========================================

interface ParkingRepository {
    void addSpot(ParkingSpot spot);
    List<ParkingSpot> findAllSpots();
    void saveTicket(Ticket ticket);
    Ticket findTicketById(String ticketId);
}

class InMemoryParkingRepository implements ParkingRepository {
    private final List<ParkingSpot> spots = new ArrayList<>();
    private final Map<String, Ticket> ticketsById = new HashMap<>();

    @Override
    public void addSpot(ParkingSpot spot) {
        spots.add(Objects.requireNonNull(spot, "Spot cannot be null"));
    }

    @Override
    public List<ParkingSpot> findAllSpots() {
        return Collections.unmodifiableList(spots);
    }

    @Override
    public void saveTicket(Ticket ticket) {
        ticketsById.put(ticket.getTicketId(), ticket);
    }

    @Override
    public Ticket findTicketById(String ticketId) {
        return ticketsById.get(ticketId);
    }
}

class ParkingLotService {
    private final ParkingRepository repository;
    private final PricingStrategy defaultPricing;
    private final PricingStrategy evPricing;

    public ParkingLotService(ParkingRepository repository,
                             PricingStrategy defaultPricing,
                             PricingStrategy evPricing) {
        this.repository = Objects.requireNonNull(repository);
        this.defaultPricing = Objects.requireNonNull(defaultPricing);
        this.evPricing = Objects.requireNonNull(evPricing);
    }

    public Ticket checkIn(Vehicle vehicle, LocalDateTime entryTime) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null");
        Objects.requireNonNull(entryTime, "Entry time cannot be null");

        ParkingSpot selectedSpot = allocateSpot(vehicle);
        selectedSpot.park(vehicle);

        Ticket ticket = new Ticket(vehicle, selectedSpot, entryTime);
        repository.saveTicket(ticket);
        return ticket;
    }

    public Receipt checkOut(String ticketId, LocalDateTime exitTime) {
        Ticket ticket = repository.findTicketById(ticketId);
        if (ticket == null || !ticket.isActive()) {
            throw new InvalidTicketException("No active ticket found for ID: " + ticketId);
        }

        ticket.close(exitTime);
        ParkingSpot spot = ticket.getSpot();
        spot.vacate();

        PricingStrategy strategy = (ticket.getVehicle() instanceof ElectricVehicle
                && spot.getSpotType() == SpotType.EV_CHARGER) ? evPricing : defaultPricing;

        BigDecimal fee = strategy.calculateFee(ticket, exitTime);
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();

        return new Receipt(
                ticket.getTicketId(),
                ticket.getVehicle().getLicensePlate(),
                spot.getSpotId(),
                minutes,
                fee
        );
    }

    public long getAvailableSpotCount() {
        return repository.findAllSpots().stream()
                .filter(ParkingSpot::isAvailable)
                .count();
    }

    private ParkingSpot allocateSpot(Vehicle vehicle) {
        List<ParkingSpot> available = repository.findAllSpots().stream()
                .filter(ParkingSpot::isAvailable)
                .filter(spot -> spotCanAccept(spot, vehicle))
                .toList();

        if (available.isEmpty()) {
            throw new NoAvailableSpotException("No available spot for vehicle " + vehicle.getLicensePlate());
        }

        // Prefer exact/specialized spot type first
        for (SpotType preferred : preferredOrder(vehicle)) {
            for (ParkingSpot spot : available) {
                if (spot.getSpotType() == preferred) {
                    return spot;
                }
            }
        }
        return available.get(0);
    }

    private boolean spotCanAccept(ParkingSpot spot, Vehicle vehicle) {
        if (!vehicle.canFitIn(spot.getSpotType())) {
            return false;
        }
        if (spot.getSpotType() == SpotType.EV_CHARGER && vehicle instanceof ElectricVehicle ev) {
            return spot.getChargingPort() != null && spot.getChargingPort().supports(ev);
        }
        return true;
    }

    private List<SpotType> preferredOrder(Vehicle vehicle) {
        return switch (vehicle.getVehicleType()) {
            case MOTORCYCLE -> List.of(SpotType.MOTORCYCLE, SpotType.COMPACT, SpotType.STANDARD);
            case CAR -> List.of(SpotType.COMPACT, SpotType.STANDARD);
            case ELECTRIC_VEHICLE -> List.of(SpotType.EV_CHARGER, SpotType.STANDARD);
        };
    }
}