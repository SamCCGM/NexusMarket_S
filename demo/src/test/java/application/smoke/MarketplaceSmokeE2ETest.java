package application.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.domain.enums.ReservationStatus;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mongodb.repository.InventoryDocumentRepository;
import application.persistence.mongodb.repository.StockReservationRepository;
import application.persistence.mysql.entity.AdministratorEntity;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.LogisticsOperatorEntity;
import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.repository.AdministratorRepository;
import application.persistence.mysql.repository.LogisticsOperatorRepository;
import application.persistence.mysql.repository.SellerRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke end-to-end del Marketplace (SDD) sin infraestructura externa:
 * H2 en modo MySQL + Mongo in-memory, pero ejercitando la aplicacion REAL
 * (HTTP, Spring Security/JWT, JPA, Mongo, servicios de dominio y puertos).
 *
 * Cubre el ciclo completo: registro/login, alta de vendedor por admin,
 * bodega + producto + stock, carrito, CreateOrder con ReserveStock y ClearCart,
 * pago, envio/despacho/entrega, factura, devolucion, reembolso, recepcion de
 * unidades con reintegro, pedido digital (entrega inmediata), cancelacion con
 * liberacion de reserva, inventario dañado y reporte administrativo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MarketplaceSmokeE2ETest {

    private static final MongoServer MONGO_SERVER = startMongoServer();
    private static final InetSocketAddress MONGO_ADDRESS = MONGO_SERVER.getLocalAddress();

    private static MongoServer startMongoServer() {
        MongoServer server = new MongoServer(new MemoryBackend());
        server.bind();
        return server;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:nexusmarket_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://" + MONGO_ADDRESS.getHostString() + ":" + MONGO_ADDRESS.getPort()
                        + "/nexusmarket_smoke");
        // Los schedulers de finalizacion/expiracion no deben interferir en el smoke.
        registry.add("nexusmarket.order-finalization-enabled", () -> "false");
        registry.add("logging.level.org.springframework.security", () -> "WARN");
        registry.add("logging.level.org.hibernate.SQL", () -> "WARN");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdministratorRepository administratorRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LogisticsOperatorRepository logisticsOperatorRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private InventoryDocumentRepository inventoryDocumentRepository;

    // Estado compartido entre las fases ordenadas del smoke
    private String buyerToken;
    private String buyerRefreshToken;
    private String sellerToken;
    private String adminToken;
    private String logisticsToken;
    private String extraBuyerToken;
    private String extraBuyerRefreshToken;
    private String extraSellerToken;
    private Long buyerId;
    private Long extraBuyerId;
    private Long warehouseId;
    private Long productId;
    private Long digitalProductId;
    private Long highStockProductId;
    private Long physicalOrderId;
    private Long physicalOrderItemId;
    private Long pendingOrderId;
    private Long returnId;

    @BeforeAll
    void seedUsers() {
        AdministratorEntity admin = administratorRepository.save(AdministratorEntity.builder()
                .fullName("Smoke Admin")
                .email("admin@smoke.local")
                .password(passwordEncoder.encode("admin12345"))
                .role(UserRole.ADMINISTRATOR)
                .status(UserStatus.ACTIVE)
                .department("QA")
                .superAdmin(true)
                .build());

        SellerEntity seller = sellerRepository.save(SellerEntity.builder()
                .fullName("Smoke Seller")
                .email("seller@smoke.local")
                .password(passwordEncoder.encode("seller12345"))
                .role(UserRole.SELLER)
                .status(UserStatus.ACTIVE)
                .companyName("Smoke Store")
                .taxId("900000001")
                .rating(4.5)
                .totalSales(0L)
                .build());

        logisticsOperatorRepository.save(LogisticsOperatorEntity.builder()
                .fullName("Smoke Logistics")
                .email("logistics@smoke.local")
                .password(passwordEncoder.encode("logistics12345"))
                .role(UserRole.LOGISTICS_OPERATOR)
                .status(UserStatus.ACTIVE)
                .companyName("Smoke Logistics Co")
                .build());

        assertNotNull(admin.getId());
    }

    // ==================== Fase 1: cuentas y seguridad ====================

    @Test
    @Order(1)
    @DisplayName("Registro publico solo BUYER y login de todos los actores")
    void accountsAndSecurity() {
        // El health check documentado debe ser publico y responder sin token.
        assertEquals(HttpStatus.OK, get("/api/health", null).getStatusCode());

        // Vendedores no pueden auto-registrarse (regla SDD)
        ResponseEntity<JsonNode> sellerSelfRegister = post("/api/auth/register", null, Map.of(
                "fullName", "Intruso", "email", "intruso@smoke.local",
                "password", "intruso12345", "role", "SELLER"));
        assertEquals(HttpStatus.FORBIDDEN, sellerSelfRegister.getStatusCode());

        // Registro publico de comprador
        ResponseEntity<JsonNode> registered = post("/api/auth/register", null, Map.of(
                "fullName", "Smoke Buyer", "email", "buyer@smoke.local",
                "password", "buyer12345", "role", "BUYER"));
        assertEquals(HttpStatus.OK, registered.getStatusCode(), body(registered));
        buyerId = registered.getBody().get("userId").asLong();
        buyerToken = registered.getBody().get("accessToken").asText();
        buyerRefreshToken = registered.getBody().get("refreshToken").asText();

        // Access y refresh son tokens distintos: un refresh no autentica
        // endpoints de negocio y un access no puede renovar credenciales.
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cart", buyerRefreshToken).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                post("/api/auth/refresh", buyerToken, null).getStatusCode());
        ResponseEntity<JsonNode> refreshed = post("/api/auth/refresh", buyerRefreshToken, null);
        assertEquals(HttpStatus.OK, refreshed.getStatusCode(), body(refreshed));
        assertNotNull(refreshed.getBody().get("accessToken").asText());

        sellerToken = login("seller@smoke.local", "seller12345");
        adminToken = login("admin@smoke.local", "admin12345");
        logisticsToken = login("logistics@smoke.local", "logistics12345");
        assertNotNull(sellerToken);

        // Un comprador no accede a endpoints administrativos
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/reports/admin", buyerToken).getStatusCode());
    }

    @Test
    @Order(2)
    @DisplayName("Admin incorpora vendedor y comprador (RegisterSeller/RegisterBuyer)")
    void adminRegistersAccounts() {
        ResponseEntity<JsonNode> newSeller = post("/api/sellers", adminToken, Map.of(
                "fullName", "Seller Extra", "email", "seller.extra@smoke.local",
                "password", "seller12345", "companyName", "Extra Store"));
        assertEquals(HttpStatus.OK, newSeller.getStatusCode(), body(newSeller));
        assertNotNull(newSeller.getBody().get("id").asLong());

        ResponseEntity<JsonNode> newBuyer = post("/api/buyers", adminToken, Map.of(
                "fullName", "Buyer Extra", "email", "buyer.extra@smoke.local",
                "password", "buyer12345"));
        assertEquals(HttpStatus.OK, newBuyer.getStatusCode(), body(newBuyer));
        extraBuyerId = newBuyer.getBody().get("id").asLong();

        // El comprador creado por el administrador debe poder autenticarse:
        // la contrasena tiene que haberse persistido cifrada.
        ResponseEntity<JsonNode> extraBuyerLogin = post("/api/auth/login", null, Map.of(
                "email", "buyer.extra@smoke.local", "password", "buyer12345"));
        assertEquals(HttpStatus.OK, extraBuyerLogin.getStatusCode(), body(extraBuyerLogin));
        extraBuyerToken = extraBuyerLogin.getBody().get("accessToken").asText();
        extraBuyerRefreshToken = extraBuyerLogin.getBody().get("refreshToken").asText();
        assertNotNull(extraBuyerToken);
        extraSellerToken = login("seller.extra@smoke.local", "seller12345");
        assertNotNull(extraSellerToken);

        // Un administrador no tiene perfil de vendedor: registrar un producto a
        // su nombre debe ser 403, no un error 500.
        assertEquals(HttpStatus.FORBIDDEN, post("/api/products", adminToken, Map.of(
                "name", "Producto Admin", "description", "No debe crearse",
                "basePrice", 10.00, "sku", "SMOKE-ADMIN-001", "category", "Pruebas")).getStatusCode());
    }

    // ==================== Fase 2: catalogo, bodega e inventario ====================

    @Test
    @Order(3)
    @DisplayName("Bodega del vendedor, producto registrado SUSPENDED y publicado + stock")
    void catalogAndStock() {
        ResponseEntity<JsonNode> warehouse = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Smoke",
                "address", "Calle 123 #45-67",
                "city", "Bogota",
                "state", "Cundinamarca",
                "country", "Colombia",
                "postalCode", "110111",
                "type", "SELLER"));
        assertEquals(HttpStatus.OK, warehouse.getStatusCode(), body(warehouse));
        warehouseId = warehouse.getBody().get("id").asLong();

        ResponseEntity<JsonNode> product = post("/api/products", sellerToken, Map.of(
                "name", "Producto Fisico Smoke",
                "description", "Producto de prueba end-to-end",
                "basePrice", 100.00,
                "sku", "SMOKE-PHY-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, product.getStatusCode(), body(product));
        productId = product.getBody().get("id").asLong();
        // Un producto se registra como borrador (SUSPENDED) y debe publicarse
        assertEquals("SUSPENDED", product.getBody().get("status").asText());

        ResponseEntity<JsonNode> published = post("/api/products/" + productId + "/publish", sellerToken, null);
        assertEquals(HttpStatus.OK, published.getStatusCode(), body(published));
        assertEquals("PUBLISHED", published.getBody().get("status").asText());

        // AddStock en la bodega PROPIA del vendedor (bodega autorizada)
        ResponseEntity<JsonNode> stock = post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseId, "productId", productId, "quantity", 10));
        assertEquals(HttpStatus.OK, stock.getStatusCode(), body(stock));
        assertEquals(10, stock.getBody().get("quantity").asInt());
    }

    // ==================== Fase 3: compra fisica completa ====================

    @Test
    @Order(4)
    @DisplayName("Carrito -> CreateOrder (reserva + ClearCart) -> pago -> envio -> entrega -> factura")
    void physicalPurchaseFlow() {
        // El comprador registra direccion de entrega
        ResponseEntity<JsonNode> updated = exchange(HttpMethod.PUT, "/api/buyers/" + buyerId, buyerToken, Map.of(
                "fullName", "Smoke Buyer",
                "street", "Cra 7 # 40-12",
                "city", "Bogota",
                "country", "Colombia",
                "postalCode", "110111"));
        assertEquals(HttpStatus.OK, updated.getStatusCode(), body(updated));

        // AddItemToCart. El cliente envia datos manipulados a proposito: el
        // carrito debe ignorarlos y tomar nombre/precio del catalogo.
        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(productId),
                "productName", "Nombre manipulado",
                "price", 1.00,
                "quantity", 2));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));
        assertEquals("Producto Fisico Smoke",
                cart.getBody().get("items").get(0).get("productName").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                cart.getBody().get("items").get(0).get("price").decimalValue()));

        // CreateOrder: valida, reserva inventario y limpia el carrito
        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        physicalOrderId = order.getBody().get("id").asLong();
        physicalOrderItemId = order.getBody().get("items").get(0).get("id").asLong();
        assertEquals("PENDING_PAYMENT", order.getBody().get("status").asText());
        assertEquals(0, new BigDecimal("200.00")
                .compareTo(order.getBody().get("totalAmount").decimalValue()));

        // ClearCart: el carrito quedo vacio
        ResponseEntity<JsonNode> emptyCart = get("/api/cart", buyerToken);
        assertEquals(HttpStatus.OK, emptyCart.getStatusCode(), body(emptyCart));
        assertEquals(0, emptyCart.getBody().get("items").size());

        // ReserveStock: 2 unidades reservadas de 10
        JsonNode inventory = inventoryRow();
        assertEquals(10, inventory.get("quantity").asInt());
        assertEquals(2, inventory.get("reservedQuantity").asInt());
        assertEquals(8, inventory.get("availableQuantity").asInt());

        // ProcessPayment
        ResponseEntity<JsonNode> paid = post("/api/orders/" + physicalOrderId + "/pay", buyerToken, null);
        assertEquals(HttpStatus.OK, paid.getStatusCode(), body(paid));
        assertEquals("PAID", paid.getBody().get("status").asText());
        assertNotNull(paid.getBody().get("paidAt"));

        // CreateShipment + DispatchShipment (SALE_EXIT consume la reserva)
        ResponseEntity<JsonNode> shipment = post(
                "/api/shipments/order/" + physicalOrderId, logisticsToken, Map.of("estimatedDays", 3));
        assertEquals(HttpStatus.OK, shipment.getStatusCode(), body(shipment));
        assertNotNull(shipment.getBody().get("trackingNumber"));
        assertNotNull(shipment.getBody().get("externalShipmentId"),
                "El envio debe pasar por ShipmentPort y guardar el id externo");

        ResponseEntity<JsonNode> dispatched = post(
                "/api/orders/" + physicalOrderId + "/dispatch", logisticsToken, null);
        assertEquals(HttpStatus.OK, dispatched.getStatusCode(), body(dispatched));
        assertEquals("DISPATCHED", dispatched.getBody().get("status").asText());

        JsonNode afterSale = inventoryRow();
        assertEquals(8, afterSale.get("quantity").asInt());
        assertEquals(0, afterSale.get("reservedQuantity").asInt());

        // ConfirmDelivery
        ResponseEntity<JsonNode> delivered = post(
                "/api/orders/" + physicalOrderId + "/confirm-delivery", logisticsToken, null);
        assertEquals(HttpStatus.OK, delivered.getStatusCode(), body(delivered));
        assertEquals("DELIVERED", delivered.getBody().get("status").asText());

        // GenerateInvoice: resultado asociado al pedido pagado
        ResponseEntity<JsonNode> invoice = get("/api/orders/" + physicalOrderId + "/invoice", buyerToken);
        assertEquals(HttpStatus.OK, invoice.getStatusCode(), body(invoice));
        assertTrue(invoice.getBody().get("invoiceNumber").asText().startsWith("INV-"));
        assertEquals(0, new BigDecimal("200.00")
                .compareTo(invoice.getBody().get("totalAmount").decimalValue()));
    }

    // ==================== Fase 4: devolucion, reembolso y reintegro ====================

    @Test
    @Order(5)
    @DisplayName("RequestReturn -> ApproveReturn -> ProcessRefund -> ReceiveReturnUnits (RETURN)")
    void returnRefundFlow() {
        ResponseEntity<JsonNode> request = post("/api/returns", buyerToken, Map.of(
                "orderId", physicalOrderId,
                "orderItemId", physicalOrderItemId,
                "reason", "Producto defectuoso",
                "quantity", 1));
        assertEquals(HttpStatus.OK, request.getStatusCode(), body(request));
        returnId = request.getBody().get("id").asLong();
        assertEquals("REQUESTED", request.getBody().get("status").asText());

        ResponseEntity<JsonNode> approved = post(
                "/api/returns/" + returnId + "/approve?resolutionNotes=Procede", adminToken, null);
        assertEquals(HttpStatus.OK, approved.getStatusCode(), body(approved));
        assertEquals("APPROVED", approved.getBody().get("status").asText());

        // La aprobacion NO toca el inventario
        assertEquals(8, inventoryRow().get("quantity").asInt());

        ResponseEntity<JsonNode> refund = post("/api/refunds/return/" + returnId, adminToken, null);
        assertEquals(HttpStatus.OK, refund.getStatusCode(), body(refund));
        assertEquals("COMPLETED", refund.getBody().get("status").asText());
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(refund.getBody().get("amount").decimalValue()));

        // Recepcion y revision: reincorpora la unidad apta al inventario
        ResponseEntity<JsonNode> received = post(
                "/api/returns/" + returnId + "/receive?warehouseId=" + warehouseId + "&damagedQuantity=0",
                logisticsToken, null);
        assertEquals(HttpStatus.OK, received.getStatusCode(), body(received));
        assertEquals("RECEIVED", received.getBody().get("status").asText());
        assertEquals(9, inventoryRow().get("quantity").asInt());

        // Ya se devolvio 1 de 2 unidades y la devolucion esta RECEIVED: pedir 2
        // mas debe rechazarse (antes se podian devolver dos veces las mismas
        // unidades porque RECEIVED no consumia el cupo devolubles).
        assertEquals(HttpStatus.CONFLICT, post("/api/returns", buyerToken, Map.of(
                "orderId", physicalOrderId,
                "orderItemId", physicalOrderItemId,
                "reason", "Intento de sobredevolucion",
                "quantity", 2)).getStatusCode());

        // Devolver el pedido de otro comprador es una violacion de autorizacion
        // (403), no una regla de negocio (409).
        assertEquals(HttpStatus.FORBIDDEN, post("/api/returns", extraBuyerToken, Map.of(
                "orderId", physicalOrderId,
                "orderItemId", physicalOrderItemId,
                "reason", "Pedido ajeno",
                "quantity", 1)).getStatusCode());

        // RequestReturn es exclusivo del comprador; el administrador no tiene
        // perfil de comprador y no debe intentar operar este flujo.
        assertEquals(HttpStatus.FORBIDDEN, post("/api/returns", adminToken, Map.of(
                "orderId", physicalOrderId,
                "orderItemId", physicalOrderItemId,
                "reason", "Intento administrativo",
                "quantity", 1)).getStatusCode());
    }

    // ==================== Fase 5: pedido digital (entrega inmediata) ====================

    @Test
    @Order(6)
    @DisplayName("Pedido 100% virtual: pago confirmado -> DELIVERED inmediato (sin DISPATCHED)")
    void digitalPurchaseFlow() {
        // Alta de producto DIGITAL por la API (producto virtual del SDD)
        ResponseEntity<JsonNode> created = post("/api/products", sellerToken, Map.of(
                "name", "Ebook Smoke",
                "description", "Producto digital de prueba",
                "basePrice", 25.00,
                "sku", "SMOKE-DIG-001",
                "category", "Pruebas",
                "productType", "DIGITAL",
                "downloadUrl", "https://example.com/ebook.zip",
                "fileFormat", "ZIP"));
        assertEquals(HttpStatus.OK, created.getStatusCode(), body(created));
        digitalProductId = created.getBody().get("id").asLong();
        assertEquals("DigitalProduct", created.getBody().get("productType").asText());
        assertEquals("https://example.com/ebook.zip",
                created.getBody().get("downloadUrl").asText());

        ResponseEntity<JsonNode> published = post(
                "/api/products/" + digitalProductId + "/publish", sellerToken, null);
        assertEquals(HttpStatus.OK, published.getStatusCode(), body(published));

        // Un producto digital sin archivo descargable no puede registrarse
        assertEquals(HttpStatus.CONFLICT, post("/api/products", sellerToken, Map.of(
                "name", "Digital sin archivo",
                "description", "Producto digital invalido",
                "basePrice", 10.00,
                "sku", "SMOKE-DIG-INVALID",
                "category", "Pruebas",
                "productType", "DIGITAL")).getStatusCode());

        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(digitalProductId),
                "productName", "Ebook Smoke",
                "price", 25.00,
                "quantity", 1));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));

        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        Long orderId = order.getBody().get("id").asLong();

        ResponseEntity<JsonNode> paid = post("/api/orders/" + orderId + "/pay", buyerToken, null);
        assertEquals(HttpStatus.OK, paid.getStatusCode(), body(paid));
        assertEquals("DELIVERED", paid.getBody().get("status").asText());

        // Un pedido virtual no admite despacho fisico (regla de negocio -> 409)
        assertEquals(HttpStatus.CONFLICT,
                post("/api/orders/" + orderId + "/dispatch", logisticsToken, null).getStatusCode());
    }

    // ==================== Fase 6: cancelacion/liberacion e inventario danado ====================

    @Test
    @Order(7)
    @DisplayName("CancelOrder libera la reserva; MarkDamaged retira disponibilidad; borrado protegido")
    void cancellationAndDamaged() {
        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(productId),
                "productName", "Producto Fisico Smoke",
                "price", 100.00,
                "quantity", 3));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));

        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        pendingOrderId = order.getBody().get("id").asLong();

        JsonNode reserved = inventoryRow();
        assertEquals(9, reserved.get("quantity").asInt());
        assertEquals(3, reserved.get("reservedQuantity").asInt());

        // CancelOrder: libera exactamente la reserva (no toca el total)
        ResponseEntity<JsonNode> cancelled = post(
                "/api/orders/" + pendingOrderId + "/cancel?reason=Prueba de cancelacion", buyerToken, null);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode(), body(cancelled));
        assertEquals("CANCELLED", cancelled.getBody().get("status").asText());

        JsonNode released = inventoryRow();
        assertEquals(9, released.get("quantity").asInt());
        assertEquals(0, released.get("reservedQuantity").asInt());

        // MarkDamaged: las unidades danadas dejan de estar disponibles
        ResponseEntity<JsonNode> damaged = post("/api/inventory/stock/damaged", logisticsToken, Map.of(
                "warehouseId", warehouseId, "productId", productId, "quantity", 2));
        assertEquals(HttpStatus.OK, damaged.getStatusCode(), body(damaged));
        assertEquals(2, damaged.getBody().get("damagedQuantity").asInt());
        assertEquals(7, damaged.getBody().get("availableQuantity").asInt());

        // Un pedido cancelado puede eliminarse; los que tienen efectos no.
        assertEquals(HttpStatus.NO_CONTENT,
                exchange(HttpMethod.DELETE, "/api/orders/" + pendingOrderId, adminToken, null).getStatusCode());
        assertTrue(stockReservationRepository.findByOrderId(pendingOrderId).isEmpty(),
                "Al borrar un pedido CANCELLED tambien se eliminan sus reservas");
    }

    // ==================== Fase 7: reporte administrativo y admin sobre productos ====================

    @Test
    @Order(8)
    @DisplayName("Reporte administrativo, factura conservada y admin puede editar/borrar productos ajenos")
    void adminReportAndProductAdministration() {
        // Documento legado sin damagedQuantity: el reporte debe tolerarlo en
        // lugar de fallar con NullPointerException.
        InventoryDocument legacy = new InventoryDocument();
        legacy.setWarehouseId(999999L);
        legacy.setProductId(999999L);
        legacy.setQuantity(1);
        legacy.setReservedQuantity(0);
        legacy.setDamagedQuantity(null);
        legacy.setMinStockLevel(10);
        legacy.setMaxStockLevel(1000);
        legacy.setStatus(application.domain.enums.InventoryStatus.AVAILABLE);
        InventoryDocument savedLegacy = inventoryDocumentRepository.save(legacy);

        ResponseEntity<JsonNode> report;
        try {
            report = get("/api/reports/admin", adminToken);
        } finally {
            inventoryDocumentRepository.deleteById(savedLegacy.getId());
        }
        assertEquals(HttpStatus.OK, report.getStatusCode(), body(report));
        JsonNode body = report.getBody();
        assertTrue(body.has("totalUsers") || body.has("users") || body.get("users") != null,
                "El reporte debe incluir usuarios: " + body);

        // El admin edita un producto de otro vendedor (bug corregido). La
        // actualizacion debe conservar y modificar tambien los campos digitales.
        ResponseEntity<JsonNode> updated = exchange(HttpMethod.PUT, "/api/products/" + digitalProductId,
                adminToken, Map.of(
                        "name", "Ebook Smoke (revisado)",
                        "description", "Producto digital de prueba - editado por admin",
                        "basePrice", 30.00,
                        "sku", "SMOKE-DIG-001",
                        "category", "Pruebas",
                        "downloadUrl", "https://example.com/ebook-v2.zip",
                        "fileSizeMb", 12,
                        "fileFormat", "PDF",
                        "licenseKeyRequired", true,
                        "maxDownloads", 5));
        assertEquals(HttpStatus.OK, updated.getStatusCode(), body(updated));
        assertEquals("https://example.com/ebook-v2.zip",
                updated.getBody().get("downloadUrl").asText());
        assertEquals(12, updated.getBody().get("fileSizeMb").asInt());
        assertEquals("PDF", updated.getBody().get("fileFormat").asText());
        assertTrue(updated.getBody().get("licenseKeyRequired").asBoolean());
        assertEquals(5, updated.getBody().get("maxDownloads").asInt());

        // Un producto ya vendido no puede borrarse: se protege su trazabilidad
        assertEquals(HttpStatus.CONFLICT,
                exchange(HttpMethod.DELETE, "/api/products/" + digitalProductId, adminToken, null)
                        .getStatusCode());

        // El admin borra un producto ajeno que no tiene pedidos asociados
        ResponseEntity<JsonNode> throwaway = post("/api/products", sellerToken, Map.of(
                "name", "Producto Desechable",
                "description", "Producto sin pedidos para probar el borrado",
                "basePrice", 10.00,
                "sku", "SMOKE-DEL-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, throwaway.getStatusCode(), body(throwaway));
        Long throwawayId = throwaway.getBody().get("id").asLong();

        assertEquals(HttpStatus.NO_CONTENT,
                exchange(HttpMethod.DELETE, "/api/products/" + throwawayId, adminToken, null)
                        .getStatusCode());
    }

    // ==================== Fase 8: autorizacion por propietario ====================

    @Test
    @Order(9)
    @DisplayName("Carrito solo BUYER; pedidos, facturas, devoluciones y envios solo del propietario")
    void ownershipAndCartAuthorization() {
        // El carrito es exclusivo del comprador: otro rol no debe entrar (403, no 500)
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cart", sellerToken).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cart", adminToken).getStatusCode());

        // Un comprador no puede leer, facturar ni pagar el pedido de otro
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/orders/" + physicalOrderId, extraBuyerToken).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/orders/" + physicalOrderId + "/invoice", extraBuyerToken).getStatusCode());

        // El propietario y el administrador si pueden consultarlo
        assertEquals(HttpStatus.OK, get("/api/orders/" + physicalOrderId, buyerToken).getStatusCode());
        assertEquals(HttpStatus.OK, get("/api/orders/" + physicalOrderId, adminToken).getStatusCode());

        // Devoluciones y envios ajenos tampoco son visibles
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/returns/" + returnId, extraBuyerToken).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/shipments/order/" + physicalOrderId, extraBuyerToken).getStatusCode());

        // Un vendedor no accede a los datos personales del comprador
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/buyers/" + buyerId, sellerToken).getStatusCode());

        // Pedido pendiente del comprador propietario
        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(productId),
                "productName", "Producto Fisico Smoke",
                "price", 100.00,
                "quantity", 1));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));

        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        Long ownedOrderId = order.getBody().get("id").asLong();

        // Otro comprador no puede pagar ese pedido
        assertEquals(HttpStatus.FORBIDDEN,
                post("/api/orders/" + ownedOrderId + "/pay", extraBuyerToken, null).getStatusCode());

        // El propietario si puede cancelarlo (libera la reserva)
        assertEquals(HttpStatus.OK,
                post("/api/orders/" + ownedOrderId + "/cancel?reason=Prueba de autorizacion",
                        buyerToken, null).getStatusCode());

        // Un pedido con productos fisicos exige direccion completa en el perfil.
        // buyer.extra no tiene direccion: debe fallar antes de reservar stock.
        assertEquals(HttpStatus.OK, post("/api/cart/items", extraBuyerToken, Map.of(
                "productId", String.valueOf(productId),
                "productName", "Producto Fisico Smoke",
                "price", 100.00,
                "quantity", 1)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                post("/api/orders", extraBuyerToken, Map.of()).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                exchange(HttpMethod.DELETE, "/api/cart", extraBuyerToken, null).getStatusCode());

        // Un comprador bloqueado pierde el acceso: el token deja de autenticar
        // aunque siga vigente criptograficamente.
        assertEquals(HttpStatus.OK,
                post("/api/users/" + extraBuyerId + "/block", adminToken, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get("/api/cart", extraBuyerToken).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                exchange(HttpMethod.DELETE, "/api/cart", extraBuyerToken, null).getStatusCode());
        // El refresh token tampoco puede renovar credenciales de una cuenta bloqueada.
        assertEquals(HttpStatus.FORBIDDEN,
                post("/api/auth/refresh", extraBuyerRefreshToken, null).getStatusCode());
    }

    // ==================== Fase 9: pago con la mayor parte del stock reservado ====================

    @Test
    @Order(10)
    @DisplayName("ProcessPayment no descuenta dos veces la reserva propia (pedido > 50% del stock)")
    void paymentWithReservedStock() {
        ResponseEntity<JsonNode> created = post("/api/products", sellerToken, Map.of(
                "name", "Producto Stock Alto",
                "description", "Producto para probar pago con reserva propia",
                "basePrice", 50.00,
                "sku", "SMOKE-HIGH-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, created.getStatusCode(), body(created));
        highStockProductId = created.getBody().get("id").asLong();

        assertEquals(HttpStatus.OK,
                post("/api/products/" + highStockProductId + "/publish", sellerToken, null).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseId, "productId", highStockProductId, "quantity", 10)).getStatusCode());

        // El pedido consume 8 de 10 unidades: al reservar quedan solo 2 disponibles.
        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(highStockProductId),
                "productName", "Producto Stock Alto",
                "price", 50.00,
                "quantity", 8));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));

        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        Long orderId = order.getBody().get("id").asLong();

        // Antes de la correccion, el pago comparaba 8 contra 2 disponibles y fallaba.
        ResponseEntity<JsonNode> paid = post("/api/orders/" + orderId + "/pay", buyerToken, null);
        assertEquals(HttpStatus.OK, paid.getStatusCode(), body(paid));
        assertEquals("PAID", paid.getBody().get("status").asText());
    }

    // ==================== Fase 10: integridad de inventario y variantes ====================

    @Test
    @Order(11)
    @DisplayName("Inventario rechaza IDs/variantes ajenos, bodegas inactivas y excesos de capacidad")
    void inventoryAndVariantIntegrity() {
        // Variante creada para otro producto
        ResponseEntity<JsonNode> foreignVariant = post(
                "/api/products/" + highStockProductId + "/variants", sellerToken, Map.of(
                        "variantName", "Variante de otro producto",
                        "price", 60.00,
                        "stock", 0));
        assertEquals(HttpStatus.OK, foreignVariant.getStatusCode(), body(foreignVariant));
        Long foreignVariantId = foreignVariant.getBody().get("id").asLong();

        // Otro vendedor no puede anadir variantes al producto ajeno
        assertEquals(HttpStatus.FORBIDDEN, post(
                "/api/products/" + productId + "/variants", extraSellerToken, Map.of(
                        "variantName", "Variante intrusa",
                        "price", 1.00,
                        "stock", 0)).getStatusCode());

        // La variante no pertenece al producto: ni inventario ni carrito la aceptan
        assertEquals(HttpStatus.CONFLICT, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseId,
                "productId", productId,
                "variantId", foreignVariantId,
                "quantity", 1)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(productId),
                "variantId", foreignVariantId,
                "productName", "Producto Fisico Smoke",
                "price", 100.00,
                "quantity", 1)).getStatusCode());

        // Un producto inexistente no puede generar una fila de inventario huerfana
        assertEquals(HttpStatus.NOT_FOUND, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseId,
                "productId", 999999,
                "quantity", 1)).getStatusCode());

        // Otro vendedor no puede operar la bodega ajena
        assertEquals(HttpStatus.FORBIDDEN, post("/api/inventory/stock/add", extraSellerToken, Map.of(
                "warehouseId", warehouseId,
                "productId", highStockProductId,
                "quantity", 1)).getStatusCode());

        // Tampoco puede leer el inventario de productos o bodegas ajenas
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/inventory/product/" + productId, extraSellerToken).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                get("/api/inventory/warehouse/" + warehouseId, extraSellerToken).getStatusCode());

        // Ni usar su propia bodega para ingresar stock de un producto ajeno
        ResponseEntity<JsonNode> extraWarehouse = post("/api/warehouses", extraSellerToken, Map.of(
                "name", "Bodega Extra Smoke",
                "address", "Calle Extra # 9-9",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 10));
        assertEquals(HttpStatus.OK, extraWarehouse.getStatusCode(), body(extraWarehouse));
        assertEquals(HttpStatus.FORBIDDEN, post("/api/inventory/stock/add", extraSellerToken, Map.of(
                "warehouseId", extraWarehouse.getBody().get("id").asLong(),
                "productId", productId,
                "quantity", 1)).getStatusCode());

        // Bodega inactiva: rechaza movimientos
        ResponseEntity<JsonNode> inactive = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Inactiva",
                "address", "Calle 1 # 2-3",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 5,
                "active", false));
        assertEquals(HttpStatus.OK, inactive.getStatusCode(), body(inactive));
        assertEquals(HttpStatus.CONFLICT, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", inactive.getBody().get("id").asLong(),
                "productId", highStockProductId,
                "quantity", 1)).getStatusCode());

        // Capacidad: una bodega de capacidad 1 no puede recibir 2 unidades
        ResponseEntity<JsonNode> small = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Capacidad Uno",
                "address", "Calle 4 # 5-6",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 1,
                "active", true));
        assertEquals(HttpStatus.OK, small.getStatusCode(), body(small));
        assertEquals(HttpStatus.CONFLICT, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", small.getBody().get("id").asLong(),
                "productId", highStockProductId,
                "quantity", 2)).getStatusCode());

        // Actualizar la cantidad de una linea revalida el producto y el vendedor.
        ResponseEntity<JsonNode> revalidated = post("/api/products", sellerToken, Map.of(
                "name", "Producto Revalidacion Carrito",
                "description", "Producto para probar revalidacion al actualizar cantidad",
                "basePrice", 12.00,
                "sku", "SMOKE-CART-REVALIDATE-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, revalidated.getStatusCode(), body(revalidated));
        Long revalidatedProductId = revalidated.getBody().get("id").asLong();
        assertEquals(HttpStatus.OK,
                post("/api/products/" + revalidatedProductId + "/publish", sellerToken, null)
                        .getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(revalidatedProductId),
                "productName", "Producto Revalidacion Carrito",
                "price", 12.00,
                "quantity", 1)).getStatusCode());

        // Si el producto se suspende despues de estar en el carrito, no se puede
        // aumentar la cantidad de esa linea (antes la actualizacion no revalidaba).
        assertEquals(HttpStatus.OK,
                post("/api/products/" + revalidatedProductId + "/suspend", sellerToken, null)
                        .getStatusCode());
        assertEquals(HttpStatus.CONFLICT, exchange(HttpMethod.PUT,
                "/api/cart/items/" + revalidatedProductId + "?quantity=2",
                buyerToken, null).getStatusCode());

        // Quitar la linea sigue siendo posible para que el comprador limpie el carrito.
        assertEquals(HttpStatus.OK, exchange(HttpMethod.DELETE,
                "/api/cart/items/" + revalidatedProductId, buyerToken, null).getStatusCode());
    }

    // ==================== Fase 11: ciclo de vida de bodegas y productos ====================

    @Test
    @Order(12)
    @DisplayName("Bodega inactiva no bloquea liberar reservas; borrado protege inventario y carrito")
    void lifecycleIntegrity() {
        // Producto y bodega aislados para no alterar el inventario del flujo principal
        ResponseEntity<JsonNode> created = post("/api/products", sellerToken, Map.of(
                "name", "Producto Ciclo de Vida",
                "description", "Producto para probar desactivacion y borrado",
                "basePrice", 20.00,
                "sku", "SMOKE-CYCLE-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, created.getStatusCode(), body(created));
        Long lifecycleProductId = created.getBody().get("id").asLong();
        assertEquals(HttpStatus.OK,
                post("/api/products/" + lifecycleProductId + "/publish", sellerToken, null).getStatusCode());

        ResponseEntity<JsonNode> warehouse = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Ciclo de Vida",
                "address", "Calle Ciclo # 1-2",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 20));
        assertEquals(HttpStatus.OK, warehouse.getStatusCode(), body(warehouse));
        Long lifecycleWarehouseId = warehouse.getBody().get("id").asLong();

        assertEquals(HttpStatus.OK, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", lifecycleWarehouseId,
                "productId", lifecycleProductId,
                "quantity", 5)).getStatusCode());

        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(lifecycleProductId),
                "productName", "Producto Ciclo de Vida",
                "price", 20.00,
                "quantity", 2)).getStatusCode());
        ResponseEntity<JsonNode> order = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
        Long lifecycleOrderId = order.getBody().get("id").asLong();

        // El pedido mantiene 2 unidades reservadas
        assertEquals(2, inventoryQuantity(lifecycleWarehouseId, lifecycleProductId, "reservedQuantity"));

        // AdjustStock no puede recortar silenciosamente una reserva existente.
        assertEquals(HttpStatus.CONFLICT, post("/api/inventory/stock/adjust", adminToken, Map.of(
                "warehouseId", lifecycleWarehouseId,
                "productId", lifecycleProductId,
                "quantity", 1)).getStatusCode());
        assertEquals(2, inventoryQuantity(lifecycleWarehouseId, lifecycleProductId, "reservedQuantity"));

        // Ajustar al limite de lo comprometido si es valido y conserva la reserva.
        assertEquals(HttpStatus.OK, post("/api/inventory/stock/adjust", adminToken, Map.of(
                "warehouseId", lifecycleWarehouseId,
                "productId", lifecycleProductId,
                "quantity", 4)).getStatusCode());
        assertEquals(4, inventoryQuantity(lifecycleWarehouseId, lifecycleProductId, "quantity"));
        assertEquals(2, inventoryQuantity(lifecycleWarehouseId, lifecycleProductId, "reservedQuantity"));

        // Se desactiva la bodega con una reserva pendiente
        ResponseEntity<JsonNode> deactivated = exchange(HttpMethod.PUT,
                "/api/warehouses/" + lifecycleWarehouseId, sellerToken, Map.of(
                        "name", "Bodega Ciclo de Vida",
                        "address", "Calle Ciclo # 1-2",
                        "type", "SELLER",
                        "active", false));
        assertEquals(HttpStatus.OK, deactivated.getStatusCode(), body(deactivated));
        assertFalse(deactivated.getBody().get("active").asBoolean());

        // La cancelacion debe poder liberar la reserva aunque la bodega este inactiva
        assertEquals(HttpStatus.OK, post(
                "/api/orders/" + lifecycleOrderId + "/cancel?reason=Prueba bodega inactiva",
                buyerToken, null).getStatusCode());
        assertEquals(0, inventoryQuantity(lifecycleWarehouseId, lifecycleProductId, "reservedQuantity"));

        // Un producto con inventario no puede borrarse
        assertEquals(HttpStatus.CONFLICT,
                exchange(HttpMethod.DELETE, "/api/products/" + lifecycleProductId, adminToken, null)
                        .getStatusCode());

        // Un producto referenciado en un carrito tampoco puede borrarse
        ResponseEntity<JsonNode> cartOnly = post("/api/products", sellerToken, Map.of(
                "name", "Producto Solo Carrito",
                "description", "Producto para probar proteccion de carrito",
                "basePrice", 5.00,
                "sku", "SMOKE-CART-ONLY-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, cartOnly.getStatusCode(), body(cartOnly));
        Long cartOnlyProductId = cartOnly.getBody().get("id").asLong();
        assertEquals(HttpStatus.OK,
                post("/api/products/" + cartOnlyProductId + "/publish", sellerToken, null).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(cartOnlyProductId),
                "productName", "Producto Solo Carrito",
                "price", 5.00,
                "quantity", 1)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                exchange(HttpMethod.DELETE, "/api/products/" + cartOnlyProductId, adminToken, null)
                        .getStatusCode());

        // Al quitar la referencia del carrito, el producto limpio si puede borrarse
        assertEquals(HttpStatus.OK,
                exchange(HttpMethod.DELETE, "/api/cart/items/" + cartOnlyProductId, buyerToken, null)
                        .getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT,
                exchange(HttpMethod.DELETE, "/api/products/" + cartOnlyProductId, adminToken, null)
                        .getStatusCode());
    }

    // ==================== Fase 12: ciclo de vida de variantes ====================

    @Test
    @Order(13)
    @DisplayName("Variantes: SKU unico, actualizacion, desactivacion y activacion")
    void variantLifecycle() {
        ResponseEntity<JsonNode> created = post("/api/products", sellerToken, Map.of(
                "name", "Producto Ciclo Variantes",
                "description", "Producto para probar el ciclo de vida de variantes",
                "basePrice", 10.00,
                "sku", "SMOKE-VAR-PROD-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, created.getStatusCode(), body(created));
        Long variantProductId = created.getBody().get("id").asLong();
        assertEquals(HttpStatus.OK,
                post("/api/products/" + variantProductId + "/publish", sellerToken, null)
                        .getStatusCode());

        ResponseEntity<JsonNode> variant = post(
                "/api/products/" + variantProductId + "/variants", sellerToken, Map.of(
                        "variantName", "Talla M",
                        "sku", "SMOKE-VAR-M-001",
                        "price", 15.00,
                        "stock", 0,
                        "attributes", "{\"talla\":\"M\"}",
                        "active", true));
        assertEquals(HttpStatus.OK, variant.getStatusCode(), body(variant));
        Long variantId = variant.getBody().get("id").asLong();
        assertTrue(variant.getBody().get("active").asBoolean());

        // El SKU de variante no puede repetirse.
        assertEquals(HttpStatus.CONFLICT, post(
                "/api/products/" + variantProductId + "/variants", sellerToken, Map.of(
                        "variantName", "Talla M duplicada",
                        "sku", "SMOKE-VAR-M-001",
                        "price", 15.00,
                        "stock", 0,
                        "active", true)).getStatusCode());

        ResponseEntity<JsonNode> secondVariant = post(
                "/api/products/" + variantProductId + "/variants", sellerToken, Map.of(
                        "variantName", "Talla S",
                        "sku", "SMOKE-VAR-S-001",
                        "price", 14.00,
                        "stock", 0,
                        "active", true));
        assertEquals(HttpStatus.OK, secondVariant.getStatusCode(), body(secondVariant));

        // En una actualizacion, el SKU de otra variante sigue estando duplicado.
        assertEquals(HttpStatus.CONFLICT, exchange(HttpMethod.PUT,
                "/api/products/" + variantProductId + "/variants/" + variantId,
                sellerToken, Map.of(
                        "variantName", "Talla M",
                        "sku", "SMOKE-VAR-S-001",
                        "price", 15.00,
                        "stock", 0,
                        "active", true)).getStatusCode());

        // Otro vendedor no puede actualizar ni desactivar variantes ajenas.
        assertEquals(HttpStatus.FORBIDDEN, exchange(HttpMethod.PUT,
                "/api/products/" + variantProductId + "/variants/" + variantId,
                extraSellerToken, Map.of(
                        "variantName", "Intrusa",
                        "sku", "SMOKE-VAR-M-001",
                        "price", 1.00,
                        "stock", 0,
                        "active", true)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                "/api/products/" + variantProductId + "/variants/" + variantId + "/deactivate",
                extraSellerToken, null).getStatusCode());

        // El propietario puede actualizar conservando su propio SKU.
        ResponseEntity<JsonNode> updated = exchange(HttpMethod.PUT,
                "/api/products/" + variantProductId + "/variants/" + variantId,
                sellerToken, Map.of(
                        "variantName", "Talla L",
                        "sku", "SMOKE-VAR-M-001",
                        "price", 16.00,
                        "stock", 0,
                        "attributes", "{\"talla\":\"L\"}",
                        "active", true));
        assertEquals(HttpStatus.OK, updated.getStatusCode(), body(updated));
        assertEquals("Talla L", updated.getBody().get("variantName").asText());
        assertEquals(16.00, updated.getBody().get("price").asDouble(), 0.001);

        // Desactivada: no puede entrar en nuevas compras.
        ResponseEntity<JsonNode> deactivated = post(
                "/api/products/" + variantProductId + "/variants/" + variantId + "/deactivate",
                sellerToken, null);
        assertEquals(HttpStatus.OK, deactivated.getStatusCode(), body(deactivated));
        assertFalse(deactivated.getBody().get("active").asBoolean());
        assertEquals(HttpStatus.CONFLICT, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(variantProductId),
                "variantId", variantId,
                "productName", "Producto Ciclo Variantes",
                "price", 16.00,
                "quantity", 1)).getStatusCode());

        // Reactivada: vuelve a ser comprable.
        ResponseEntity<JsonNode> activated = post(
                "/api/products/" + variantProductId + "/variants/" + variantId + "/activate",
                sellerToken, null);
        assertEquals(HttpStatus.OK, activated.getStatusCode(), body(activated));
        assertTrue(activated.getBody().get("active").asBoolean());
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(variantProductId),
                "variantId", variantId,
                "productName", "Producto Ciclo Variantes",
                "price", 16.00,
                "quantity", 1)).getStatusCode());
        assertEquals(HttpStatus.OK, exchange(HttpMethod.DELETE,
                "/api/cart/items/" + variantProductId + "?variantId=" + variantId,
                buyerToken, null).getStatusCode());
    }

    // ==================== Fase 13: validacion de contrato de entrada ====================

    @Test
    @Order(14)
    @DisplayName("Los DTO rechazan formatos y longitudes invalidas con 400, no con 500")
    void inputValidationContract() {
        // Login: el email debe tener formato valido.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/login", null, Map.of(
                "email", "no-es-un-email",
                "password", "buyer12345")).getStatusCode());

        // Pedido: moneda ISO de tres letras y notas acotadas.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/orders", buyerToken, Map.of(
                "currency", "US")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/orders", buyerToken, Map.of(
                "notes", "x".repeat(1001))).getStatusCode());

        // Devolucion: el motivo tiene un limite razonable.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/returns", buyerToken, Map.of(
                "orderId", physicalOrderId,
                "orderItemId", physicalOrderItemId,
                "reason", "x".repeat(1001),
                "quantity", 1)).getStatusCode());

        // Producto: tipo valido y peso positivo.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/products", sellerToken, Map.of(
                "name", "Producto Tipo Invalido",
                "description", "No debe crearse",
                "basePrice", 10.00,
                "sku", "SMOKE-INVALID-TYPE-001",
                "category", "Pruebas",
                "productType", "OTHER")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/products", sellerToken, Map.of(
                "name", "Producto Peso Invalido",
                "description", "No debe crearse",
                "basePrice", 10.00,
                "sku", "SMOKE-INVALID-WEIGHT-001",
                "category", "Pruebas",
                "weight", -1)).getStatusCode());

        // Actualizaciones parciales: textos dentro de rango.
        assertEquals(HttpStatus.BAD_REQUEST, exchange(HttpMethod.PUT,
                "/api/buyers/" + buyerId, buyerToken, Map.of("fullName", "A")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, exchange(HttpMethod.PUT,
                "/api/users/" + buyerId, adminToken, Map.of("fullName", "A")).getStatusCode());

        // Envio: dias estimados no negativos y parametro de ruta numerico.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/shipments/order/" + physicalOrderId,
                logisticsToken, Map.of("estimatedDays", -1)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                get("/api/inventory/warehouse/no-es-numero", adminToken).getStatusCode());
    }

    // ==================== Fase 14: reservas trazables por pedido ====================

    @Test
    @Order(15)
    @DisplayName("Cada pedido conserva y libera/consume solo sus propias reservas de inventario")
    void perOrderReservationTraceability() {
        // Se parte de un carrito limpio para aislar el escenario.
        assertEquals(HttpStatus.NO_CONTENT,
                exchange(HttpMethod.DELETE, "/api/cart", buyerToken, null).getStatusCode());

        ResponseEntity<JsonNode> created = post("/api/products", sellerToken, Map.of(
                "name", "Producto Reservas Trazables",
                "description", "Producto para probar reservas exactas por pedido",
                "basePrice", 40.00,
                "sku", "SMOKE-RESERVATION-001",
                "category", "Pruebas"));
        assertEquals(HttpStatus.OK, created.getStatusCode(), body(created));
        Long reservationProductId = created.getBody().get("id").asLong();
        assertEquals(HttpStatus.OK,
                post("/api/products/" + reservationProductId + "/publish", sellerToken, null).getStatusCode());

        ResponseEntity<JsonNode> warehouseA = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Reservas A",
                "address", "Calle Reservas # 1-1",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 50));
        assertEquals(HttpStatus.OK, warehouseA.getStatusCode(), body(warehouseA));
        Long warehouseAId = warehouseA.getBody().get("id").asLong();

        ResponseEntity<JsonNode> warehouseB = post("/api/warehouses", sellerToken, Map.of(
                "name", "Bodega Reservas B",
                "address", "Calle Reservas # 2-2",
                "city", "Bogota",
                "country", "Colombia",
                "type", "SELLER",
                "capacity", 50));
        assertEquals(HttpStatus.OK, warehouseB.getStatusCode(), body(warehouseB));
        Long warehouseBId = warehouseB.getBody().get("id").asLong();

        assertEquals(HttpStatus.OK, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseAId, "productId", reservationProductId, "quantity", 6)).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/inventory/stock/add", sellerToken, Map.of(
                "warehouseId", warehouseBId, "productId", reservationProductId, "quantity", 5)).getStatusCode());

        // Pedido A: 2 unidades. La bodega A (6) tiene mas disponible que B (5),
        // por lo que la asignacion queda en A.
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(reservationProductId),
                "productName", "Producto Reservas Trazables",
                "price", 40.00,
                "quantity", 2)).getStatusCode());
        ResponseEntity<JsonNode> orderA = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, orderA.getStatusCode(), body(orderA));
        Long orderAId = orderA.getBody().get("id").asLong();
        Long orderAItemId = orderA.getBody().get("items").get(0).get("id").asLong();

        // Pedido B: 4 unidades. Tras A, la bodega B (5) supera a A (4).
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(reservationProductId),
                "productName", "Producto Reservas Trazables",
                "price", 40.00,
                "quantity", 4)).getStatusCode());
        ResponseEntity<JsonNode> orderB = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, orderB.getStatusCode(), body(orderB));
        Long orderBId = orderB.getBody().get("id").asLong();
        Long orderBItemId = orderB.getBody().get("items").get(0).get("id").asLong();

        var reservationsA = stockReservationRepository.findByOrderId(orderAId);
        var reservationsB = stockReservationRepository.findByOrderId(orderBId);
        assertEquals(1, reservationsA.size());
        assertEquals(1, reservationsB.size());
        assertEquals(orderAItemId, reservationsA.get(0).getOrderItemId());
        assertEquals(orderBItemId, reservationsB.get(0).getOrderItemId());
        assertEquals(warehouseAId, reservationsA.get(0).getWarehouseId());
        assertEquals(warehouseBId, reservationsB.get(0).getWarehouseId());
        assertEquals(2, reservationsA.get(0).getQuantity());
        assertEquals(4, reservationsB.get(0).getQuantity());
        assertEquals(ReservationStatus.ACTIVE, reservationsA.get(0).getStatus());
        assertEquals(ReservationStatus.ACTIVE, reservationsB.get(0).getStatus());

        assertEquals(2, inventoryQuantity(warehouseAId, reservationProductId, "reservedQuantity"));
        assertEquals(4, inventoryQuantity(warehouseBId, reservationProductId, "reservedQuantity"));

        // Cancelar A solo libera A: la reserva de B (bodega B) queda intacta.
        ResponseEntity<JsonNode> cancelled = post(
                "/api/orders/" + orderAId + "/cancel?reason=Pedido A cancelado", buyerToken, null);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode(), body(cancelled));
        assertEquals("CANCELLED", cancelled.getBody().get("status").asText());

        assertEquals(0, inventoryQuantity(warehouseAId, reservationProductId, "reservedQuantity"));
        assertEquals(4, inventoryQuantity(warehouseBId, reservationProductId, "reservedQuantity"));
        assertEquals(6, inventoryQuantity(warehouseAId, reservationProductId, "quantity"));
        assertEquals(5, inventoryQuantity(warehouseBId, reservationProductId, "quantity"));

        var reservationsAAfterCancel = stockReservationRepository.findByOrderId(orderAId);
        assertEquals(ReservationStatus.RELEASED, reservationsAAfterCancel.get(0).getStatus());
        var reservationsBAfterCancel = stockReservationRepository.findByOrderId(orderBId);
        assertEquals(ReservationStatus.ACTIVE, reservationsBAfterCancel.get(0).getStatus());

        // B se paga y despacha: consume exactamente sus 4 unidades de B.
        assertEquals(HttpStatus.OK, post("/api/orders/" + orderBId + "/pay", buyerToken, null).getStatusCode());
        assertEquals(HttpStatus.OK, post("/api/shipments/order/" + orderBId,
                logisticsToken, Map.of("estimatedDays", 2)).getStatusCode());
        ResponseEntity<JsonNode> dispatched = post("/api/orders/" + orderBId + "/dispatch",
                logisticsToken, null);
        assertEquals(HttpStatus.OK, dispatched.getStatusCode(), body(dispatched));
        assertEquals("DISPATCHED", dispatched.getBody().get("status").asText());

        assertEquals(6, inventoryQuantity(warehouseAId, reservationProductId, "quantity"));
        assertEquals(0, inventoryQuantity(warehouseAId, reservationProductId, "reservedQuantity"));
        assertEquals(1, inventoryQuantity(warehouseBId, reservationProductId, "quantity"));
        assertEquals(0, inventoryQuantity(warehouseBId, reservationProductId, "reservedQuantity"));

        var reservationsBAfterDispatch = stockReservationRepository.findByOrderId(orderBId);
        assertEquals(ReservationStatus.CONSUMED, reservationsBAfterDispatch.get(0).getStatus());

        // Una bodega inactiva con stock no debe contar como disponibilidad ni
        // bloquear un pedido que si puede cubrirse con otra bodega activa.
        ResponseEntity<JsonNode> deactivatedA = exchange(HttpMethod.PUT,
                "/api/warehouses/" + warehouseAId, sellerToken, Map.of(
                        "name", "Bodega Reservas A",
                        "address", "Calle Reservas # 1-1",
                        "city", "Bogota",
                        "country", "Colombia",
                        "type", "SELLER",
                        "active", false));
        assertEquals(HttpStatus.OK, deactivatedA.getStatusCode(), body(deactivatedA));
        assertFalse(deactivatedA.getBody().get("active").asBoolean());

        assertEquals(0, inventoryQuantity(warehouseAId, reservationProductId, "reservedQuantity"));
        assertEquals(HttpStatus.OK, post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(reservationProductId),
                "productName", "Producto Reservas Trazables",
                "price", 40.00,
                "quantity", 1)).getStatusCode());
        ResponseEntity<JsonNode> orderC = post("/api/orders", buyerToken, Map.of());
        assertEquals(HttpStatus.OK, orderC.getStatusCode(), body(orderC));
        Long orderCId = orderC.getBody().get("id").asLong();

        // La unica unidad disponible esta en la bodega B activa.
        assertEquals(1, inventoryQuantity(warehouseBId, reservationProductId, "reservedQuantity"));
        assertEquals(HttpStatus.OK, post(
                "/api/orders/" + orderCId + "/cancel?reason=Fin de prueba de bodega inactiva",
                buyerToken, null).getStatusCode());
        assertEquals(0, inventoryQuantity(warehouseBId, reservationProductId, "reservedQuantity"));
    }

    // ==================== Helpers ====================

    private int inventoryQuantity(Long warehouseId, Long productId, String field) {
        ResponseEntity<JsonNode> response = get("/api/inventory/warehouse/" + warehouseId, adminToken);
        assertEquals(HttpStatus.OK, response.getStatusCode(), body(response));
        for (JsonNode row : response.getBody()) {
            if (row.get("productId").asLong() == productId
                    && (row.get("variantId") == null || row.get("variantId").isNull())) {
                return row.get(field).asInt();
            }
        }
        fail("No se encontro fila de inventario para warehouse=" + warehouseId + ", product=" + productId);
        return 0;
    }

    private JsonNode inventoryRow() {
        ResponseEntity<JsonNode> response = get("/api/inventory/warehouse/" + warehouseId, adminToken);
        assertEquals(HttpStatus.OK, response.getStatusCode(), body(response));
        for (JsonNode row : response.getBody()) {
            if (row.get("productId").asLong() == productId
                    && (row.get("variantId") == null || row.get("variantId").isNull())) {
                return row;
            }
        }
        fail("No se encontro fila de inventario para el producto " + productId);
        return null;
    }

    private String login(String email, String password) {
        ResponseEntity<JsonNode> response = post("/api/auth/login", null, Map.of(
                "email", email, "password", password));
        assertEquals(HttpStatus.OK, response.getStatusCode(), body(response));
        return response.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private ResponseEntity<JsonNode> post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        return rest.exchange("http://localhost:" + port + path, method, request, JsonNode.class);
    }

    private String body(ResponseEntity<JsonNode> response) {
        return "status=" + response.getStatusCode() + " body=" + response.getBody();
    }
}

