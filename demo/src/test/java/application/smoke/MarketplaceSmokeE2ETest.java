package application.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import application.domain.enums.BuyerStatus;
import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.domain.service.ProductService;
import application.persistence.mysql.entity.AdministratorEntity;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.DigitalProductEntity;
import application.persistence.mysql.entity.LogisticsOperatorEntity;
import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.repository.AdministratorRepository;
import application.persistence.mysql.repository.BuyerRepository;
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
import java.util.List;
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
    private BuyerRepository buyerRepository;

    @Autowired
    private LogisticsOperatorRepository logisticsOperatorRepository;

    @Autowired
    private ProductService productService;

    // Estado compartido entre las fases ordenadas del smoke
    private String buyerToken;
    private String sellerToken;
    private String adminToken;
    private String logisticsToken;
    private Long buyerId;
    private Long sellerId;
    private Long warehouseId;
    private Long productId;
    private Long digitalProductId;
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
        sellerId = seller.getId();

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

        // AddItemToCart
        ResponseEntity<JsonNode> cart = post("/api/cart/items", buyerToken, Map.of(
                "productId", String.valueOf(productId),
                "productName", "Producto Fisico Smoke",
                "price", 100.00,
                "quantity", 2));
        assertEquals(HttpStatus.OK, cart.getStatusCode(), body(cart));

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
    }

    // ==================== Fase 5: pedido digital (entrega inmediata) ====================

    @Test
    @Order(6)
    @DisplayName("Pedido 100% virtual: pago confirmado -> DELIVERED inmediato (sin DISPATCHED)")
    void digitalPurchaseFlow() {
        DigitalProductEntity digital = new DigitalProductEntity();
        digital.setName("Ebook Smoke");
        digital.setDescription("Producto digital de prueba");
        digital.setBasePrice(new BigDecimal("25.00"));
        digital.setSku("SMOKE-DIG-001");
        digital.setCategory("Pruebas");
        digital.setDownloadUrl("https://example.com/ebook.zip");
        digital.setSeller(sellerRepository.findById(sellerId).orElseThrow());

        DigitalProductEntity registered = (DigitalProductEntity) productService.registerProduct(digital);
        digitalProductId = registered.getId();
        productService.publishProduct(digitalProductId);

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
    }

    // ==================== Fase 7: reporte administrativo y admin sobre productos ====================

    @Test
    @Order(8)
    @DisplayName("Reporte administrativo, factura conservada y admin puede editar/borrar productos ajenos")
    void adminReportAndProductAdministration() {
        ResponseEntity<JsonNode> report = get("/api/reports/admin", adminToken);
        assertEquals(HttpStatus.OK, report.getStatusCode(), body(report));
        JsonNode body = report.getBody();
        assertTrue(body.has("totalUsers") || body.has("users") || body.get("users") != null,
                "El reporte debe incluir usuarios: " + body);

        // El admin edita un producto de otro vendedor (bug corregido)
        ResponseEntity<JsonNode> updated = exchange(HttpMethod.PUT, "/api/products/" + digitalProductId,
                adminToken, Map.of(
                        "name", "Ebook Smoke (revisado)",
                        "description", "Producto digital de prueba - editado por admin",
                        "basePrice", 30.00,
                        "sku", "SMOKE-DIG-001",
                        "category", "Pruebas"));
        assertEquals(HttpStatus.OK, updated.getStatusCode(), body(updated));

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

    // ==================== Helpers ====================

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
