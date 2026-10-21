import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Fuente de extracto-oms.jar. SQL copiado de consulta.extracto-bc-* (queries.properties); SBB partido por tipo de artículo.
// Compilar: javac --release 8 Extractor.java && jar uf ../extracto-oms.jar Extractor.class
public class Extractor {

    static final String SQL_LP_SL = "SELECT tip.remision FROM BRIDGECORE.tx_informacion_procesada tip INNER JOIN BRIDGECORE.tx tx ON tip.id = tx.id_info_procesada WHERE tx.id_tipo_articulo = '1' AND tip.id_cat_estatus = 0 AND tx.id_tipo_tx = 1 AND tip.atg_ship_grp_id IS NOT NULL AND tip.atg_order_id IS NOT NULL AND tip.remision IS NOT NULL AND tip.total_cobrado > 0 AND tip.is_click_and_collct = 'N' AND tip.rem_type_gr <> '2' AND tip.FECHA_TX_COMPRA BETWEEN ? AND ? ORDER BY tip.FECHA_TX_COMPRA ASC";
    static final String SQL_LP_BT = "SELECT tip.orden_venta FROM BRIDGECORE.tx_informacion_procesada tip INNER JOIN BRIDGECORE.tx tx ON tip.id = tx.id_info_procesada WHERE tx.id_tipo_articulo = '0' AND tip.id_cat_estatus = 0 AND tx.id_tipo_tx = 1 AND tip.atg_ship_grp_id IS NOT NULL AND tip.atg_order_id IS NOT NULL AND tip.orden_venta IS NOT NULL AND tip.total_cobrado > 0 AND tip.is_click_and_collct = 'N' AND tip.rem_type_gr <> '2' AND tip.FECHA_TX_COMPRA BETWEEN ? AND ? ORDER BY tip.FECHA_TX_COMPRA ASC";
    static final String SQL_SBB = "SELECT tip.atg_ship_grp_id FROM BRIDGECORE2.tx_informacion_procesada tip INNER JOIN BRIDGECORE2.tx tx ON tip.id = tx.id_info_procesada WHERE tx.id_tipo_articulo = '%s' AND tip.id_cat_estatus = 0 AND tx.id_tipo_tx = 1 AND tip.atg_ship_grp_id IS NOT NULL AND tip.atg_order_id IS NOT NULL AND tip.total_cobrado > 0 AND tip.FECHA_TX_COMPRA BETWEEN ? AND ? ORDER BY tip.FECHA_TX_COMPRA ASC";

    // {carpeta, prefijo, sql}
    static final String[][] SALIDAS = {
            {"liverpool", "lp_sl", SQL_LP_SL},
            {"liverpool", "lp_bt", SQL_LP_BT},
            {"suburbia", "sbb_sl", String.format(SQL_SBB, "1")},
            {"suburbia", "sbb_bt", String.format(SQL_SBB, "0")},
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Uso: Extractor <properties> <inicio 'yyyy-MM-dd HH:mm:ss'> <fin> <sello yyyyMMdd> <baseDir>");
            System.exit(2);
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(args[0]))) {
            props.load(in);
        }
        String url = props.getProperty("oracle.url", "").trim();
        String user = props.getProperty("oracle.user", "").trim();
        String pass = props.getProperty("oracle.password", "");
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            System.err.println("Faltan oracle.url, oracle.user u oracle.password en " + args[0]);
            System.exit(2);
        }
        Timestamp inicio = Timestamp.valueOf(args[1]);
        Timestamp fin = Timestamp.valueOf(args[2]);
        String sello = args[3];
        Path base = Paths.get(args[4]);

        boolean fallo = false;
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            for (String[] salida : SALIDAS) {
                String nombre = salida[0] + "/" + salida[1] + "_" + sello + ".csv";
                try {
                    List<String> filas = consultar(conn, salida[2], inicio, fin);
                    escribir(base.resolve(salida[0]), salida[1] + "_" + sello + ".csv", filas);
                    System.out.println(nombre + ": " + filas.size());
                } catch (Exception e) {
                    System.out.println(nombre + ": Error: " + e.getMessage());
                    fallo = true;
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR de conexion a Oracle: " + e.getMessage());
            System.exit(1);
        }
        System.exit(fallo ? 1 : 0);
    }

    static List<String> consultar(Connection conn, String sql, Timestamp inicio, Timestamp fin) throws Exception {
        List<String> filas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, inicio);
            ps.setTimestamp(2, fin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String valor = rs.getString(1);
                    if (valor != null) {
                        filas.add(valor);
                    }
                }
            }
        }
        return filas;
    }

    static void escribir(Path dir, String archivo, List<String> filas) throws Exception {
        String contenido = filas.isEmpty() ? "" : String.join("\n", filas) + "\n";
        Files.createDirectories(dir);
        Files.write(dir.resolve(archivo), contenido.getBytes(StandardCharsets.UTF_8));
    }
}
