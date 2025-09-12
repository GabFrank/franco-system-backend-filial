// FUNCIÓN PARA EL CÁLCULO DEL DÍGITO VERIFICADOR EN JAVASCRIPT
// Basada en la función PL/SQL original

function calcularDigitoVerificador(numero, basemax = 11) {
    let numeroAl = '';
    
    // Cambia la ultima letra por ascii en caso que la cedula termine en letra
    for (let i = 0; i < numero.length; i++) {
        let caracter = numero.charAt(i).toUpperCase();
        let ascii = caracter.charCodeAt(0);
        
        if (ascii < 48 || ascii > 57) { // de 0 a 9
            numeroAl += ascii;
        } else {
            numeroAl += caracter;
        }
    }
    
    // Calcula el DV
    let k = 2;
    let total = 0;
    
    for (let i = numeroAl.length - 1; i >= 0; i--) {
        if (k > basemax) {
            k = 2;
        }
        let numeroAux = parseInt(numeroAl.charAt(i));
        total += (numeroAux * k);
        k++;
    }
    
    let resto = total % 11;
    let digito;
    
    if (resto > 1) {
        digito = 11 - resto;
    } else {
        digito = 0;
    }
    
    return digito;
}

// Función de prueba
function testDigitoVerificador() {
    const numero = "0144444401700100100145282201701251587326098";
    console.log("Número a procesar:", numero);
    
    const dv = calcularDigitoVerificador(numero);
    console.log("Dígito Verificador calculado:", dv);
    
    // Mostrar el proceso paso a paso
    console.log("\n--- PROCESO DETALLADO ---");
    let numeroAl = '';
    for (let i = 0; i < numero.length; i++) {
        let caracter = numero.charAt(i).toUpperCase();
        let ascii = caracter.charCodeAt(0);
        
        if (ascii < 48 || ascii > 57) {
            numeroAl += ascii;
        } else {
            numeroAl += caracter;
        }
    }
    console.log("Número convertido:", numeroAl);
    
    let k = 2;
    let total = 0;
    let proceso = [];
    
    for (let i = numeroAl.length - 1; i >= 0; i--) {
        if (k > 11) {
            k = 2;
        }
        let numeroAux = parseInt(numeroAl.charAt(i));
        let producto = numeroAux * k;
        total += producto;
        proceso.push(`${numeroAux} × ${k} = ${producto}`);
        k++;
    }
    
    console.log("Productos (de derecha a izquierda):");
    proceso.forEach((p, index) => {
        console.log(`${proceso.length - index}. ${p}`);
    });
    
    console.log("Total:", total);
    let resto = total % 11;
    console.log("Resto:", resto);
    console.log("DV:", resto > 1 ? 11 - resto : 0);
}

// Ejecutar la prueba
testDigitoVerificador();