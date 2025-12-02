const http = require('http');
const fs = require('fs');
const path = require('path');

/**
 * Service Discovery para Prometheus - Versión JavaScript
 * Obtiene servidores activos del API Gateway y genera targets dinámicos
 */

const GATEWAY_URL = 'http://host.docker.internal:8091/gateway/api/servers/status';
const TARGETS_FILE = '/etc/prometheus/targets/rest-servers.json';
const POLL_INTERVAL = 10000; // 10 segundos

console.log('🔍 Iniciando Service Discovery para Prometheus...');
console.log(`Gateway URL: ${GATEWAY_URL}`);
console.log(`Targets File: ${TARGETS_FILE}`);
console.log(`Poll Interval: ${POLL_INTERVAL}ms (${POLL_INTERVAL/1000}s)`);

// Cache para evitar escrituras innecesarias
let lastTargetsHash = '';

function makeRequest(url) {
    return new Promise((resolve, reject) => {
        const request = http.get(url, { timeout: 10000 }, (response) => {
            let data = '';
            
            response.on('data', chunk => {
                data += chunk;
            });
            
            response.on('end', () => {
                if (response.statusCode === 200) {
                    try {
                        const jsonData = JSON.parse(data);
                        resolve(jsonData);
                    } catch (error) {
                        reject(new Error(`Error parsing JSON: ${error.message}`));
                    }
                } else {
                    reject(new Error(`HTTP ${response.statusCode}: ${response.statusText}`));
                }
            });
        });
        
        request.on('error', (error) => {
            reject(error);
        });
        
        request.on('timeout', () => {
            request.destroy();
            reject(new Error('Request timeout after 10 seconds'));
        });
    });
}

async function discoverServers() {
    try {
        console.log(`[${new Date().toISOString()}] 🔄 Polling Gateway for active servers...`);
        
        const response = await makeRequest(GATEWAY_URL);
        
        if (!response.success) {
            throw new Error(response.message || 'Gateway returned error');
        }
        
        const targets = [];
        let activeCount = 0;
        
        // Procesar servidores activos del Gateway
        if (response.servers) {
            for (const [serverId, serverData] of Object.entries(response.servers)) {
                if (serverData.status === 'ACTIVE' && serverData.config) {
                    // Convertir localhost a host.docker.internal para acceso desde contenedor
                    let targetHost = serverData.config.host;
                    if (targetHost === 'localhost' || targetHost === '127.0.0.1') {
                        targetHost = 'host.docker.internal';
                    }
                    
                    const target = {
                        targets: [`${targetHost}:${serverData.config.port}`],
                        labels: {
                            job: 'rest-servers',
                            server_id: serverId,
                            server_name: serverData.config.name || serverId,
                            instance: `${serverData.config.host}:${serverData.config.port}` // Mantener original para display
                        }
                    };
                    
                    targets.push(target);
                    activeCount++;
                    console.log(`  ✅ Server: ${serverId} (REST: ${serverData.config.host}:${serverData.config.port})`);
                }
            }
        }
        
        // Escribir archivo de targets para Prometheus
        const targetsDir = path.dirname(TARGETS_FILE);
        
        // Crear directorio si no existe
        try {
            fs.mkdirSync(targetsDir, { recursive: true });
        } catch (error) {
            // Ignorar si ya existe
        }
        
        // Escribir archivo JSON solo si cambió para evitar fluctuaciones
        const targetsJson = JSON.stringify(targets, null, 2);
        const currentHash = require('crypto').createHash('sha256').update(targetsJson).digest('hex');
        
        if (currentHash !== lastTargetsHash) {
            fs.writeFileSync(TARGETS_FILE, targetsJson);
            lastTargetsHash = currentHash;
            console.log(`✅ Discovery completed: ${activeCount} active servers written to ${TARGETS_FILE}`);
        } else {
            console.log(`🔄 No changes detected, skipping file write (${activeCount} servers)`);
        }
        
        if (activeCount > 0) {
            console.log(`📊 Targets generated for Prometheus scraping:`);
            targets.forEach(target => {
                console.log(`   - ${target.targets[0]} (${target.labels.server_name})`);
            });
        } else {
            console.log(`⚠️  No active servers found in Gateway`);
        }
        
    } catch (error) {
        console.error(`❌ Error during service discovery: ${error.message}`);
        
        // Crear archivo vacío en caso de error para evitar que Prometheus falle
        try {
            fs.writeFileSync(TARGETS_FILE, '[]');
            console.log(`📝 Empty targets file written to prevent Prometheus errors`);
        } catch (writeError) {
            console.error(`❌ Failed to write empty targets file: ${writeError.message}`);
        }
    }
}

// Función principal
async function main() {
    console.log('🚀 Service Discovery started!');
    
    // Primera ejecución inmediata
    await discoverServers();
    
    // Ejecutar cada 10 segundos para mejor sincronización
    setInterval(async () => {
        await discoverServers();
    }, POLL_INTERVAL);
}

// Manejar señales de terminación
process.on('SIGINT', () => {
    console.log('\n⏹️  Service Discovery stopping...');
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\n⏹️  Service Discovery stopping...');
    process.exit(0);
});

// Iniciar
main().catch(error => {
    console.error(`💥 Fatal error: ${error.message}`);
    process.exit(1);
});