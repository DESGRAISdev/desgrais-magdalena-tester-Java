package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() throws Exception {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterEach
    private void cleanTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown() {

    }

    @Test
    public void testParkingACar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        //TODO: check that a ticket is actualy saved in DB and Parking table is updated with availability
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket);
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());
        assertFalse(ticket.getParkingSpot().isAvailable());
    }

    @Test
    public void testParkingLotExit() throws Exception {

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE ticket SET IN_TIME = ? WHERE VEHICLE_REG_NUMBER = 'ABCDEF' AND OUT_TIME IS NULL")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - (60 * 60 * 1000)));
            ps.executeUpdate();
        }
        parkingService.processExitingVehicle();
        double price = -1;
        Timestamp outTime = null;

        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT PRICE, OUT_TIME FROM ticket WHERE VEHICLE_REG_NUMBER = ? ORDER BY IN_TIME DESC LIMIT 1")) {
            ps.setString(1, "ABCDEF");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                price = rs.getDouble("PRICE");
                outTime = rs.getTimestamp("OUT_TIME");
            }
        }
        assertNotNull(outTime, "L'heure de sortie doit être enregistrée en BDD");
        assertTrue(price >= 0, "Le prix généré doit être supérieur ou égal à 0");
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);


        parkingService.processIncomingVehicle();
        Ticket ticket1 = ticketDAO.getTicket("ABCDEF");
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE ticket SET IN_TIME = ? WHERE ID = ?")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - (120 * 60 * 1000)));
            ps.setInt(2, ticket1.getId());
            ps.executeUpdate();
        }
        parkingService.processExitingVehicle();


        parkingService.processIncomingVehicle();
        Ticket ticket2 = ticketDAO.getTicket("ABCDEF");
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE ticket SET IN_TIME = ? WHERE ID = ?")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - (60 * 60 * 1000)));
            ps.setInt(2, ticket2.getId());
            ps.executeUpdate();
        }
        parkingService.processExitingVehicle();

        Ticket updatedTicket = ticketDAO.getTicket("ABCDEF");
        assertEquals(1.43, updatedTicket.getPrice(), 0.01);
    }
}

