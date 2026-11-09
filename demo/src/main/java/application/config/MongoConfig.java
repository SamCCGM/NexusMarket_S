package application.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.net.InetSocketAddress;

@Configuration
@EnableMongoRepositories(basePackages = "application.persistence.mongodb.repository")
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Autowired
    private ObjectProvider<MongoServer> localMongoServerProvider;

    @Override
    protected String getDatabaseName() {
        // La base de datos se toma de la propia URI de conexion.
        ConnectionString connectionString = new ConnectionString(mongoUri);
        String database = connectionString.getDatabase();
        return database != null && !database.isBlank() ? database : "nexusmarket_mongo";
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        String resolvedUri = mongoUri;

        // En el perfil "local" hay un MongoServer en memoria levantado por
        // LocalMongoConfig. Se usa su puerto real (puede ser aleatorio) en
        // lugar de la URI externa configurada en application.properties.
        MongoServer localServer = localMongoServerProvider.getIfAvailable();
        if (localServer != null) {
            InetSocketAddress address = localServer.getLocalAddress();
            resolvedUri = "mongodb://" + address.getHostString() + ":" + address.getPort()
                    + "/" + getDatabaseName();
        }

        ConnectionString connectionString = new ConnectionString(resolvedUri);
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(mongoClientSettings);
    }
}