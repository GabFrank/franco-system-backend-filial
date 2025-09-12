import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.utilitarios.DateUtils;

public class TestCDC {
    public static void main(String[] args) {
        String strDate = "2017-01-25 15:00";
        System.out.println("Resultado esperado: 01444444017001001001452822017012515873260988");
        System.out.println("Resultado obtenido: " + FacturaLegalGraphQL.generarCdc("44444401", "001", "001", 14528, 2, DateUtils.stringToDate(strDate)));
    }
}
