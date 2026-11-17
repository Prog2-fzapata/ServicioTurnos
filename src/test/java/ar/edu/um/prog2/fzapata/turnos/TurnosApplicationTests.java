package ar.edu.um.prog2.fzapata.turnos;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TurnosApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

	// Sin base un pedido espera 5 s por una conexión y falla con 503, no los 30 s por defecto del pool
	@Test
	void thePoolWaitsFiveSecondsForAConnection() throws Exception {
		assertThat(dataSource.unwrap(HikariDataSource.class).getConnectionTimeout()).isEqualTo(5_000L);
	}

}
