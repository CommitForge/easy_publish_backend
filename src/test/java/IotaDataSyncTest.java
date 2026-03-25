import com.easypublish.EasyPublishApplication;
import com.easypublish.batch.IotaDataSync;
import com.easypublish.batch.BlockEmitterIndexer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = EasyPublishApplication.class)
@ActiveProfiles("test")
public class IotaDataSyncTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private IotaDataSync sync;

    @BeforeAll
    static void setupClass() {
        // Use an in-memory H2 database for testing
        emf = Persistence.createEntityManagerFactory("test-pu");
    }

    @AfterAll
    static void tearDownClass() {
        if (emf != null) {
            emf.close();
        }
    }

    @BeforeEach
    void setup() {
        em = emf.createEntityManager();
     //   sync = new IotaDataSync(em);
    }

    @AfterEach
    void tearDown() {
        if (em != null) {
            em.close();
        }
    }

}
