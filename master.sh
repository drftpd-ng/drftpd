VMARGS="
	-server
	-Djava.library.path=lib \
	-Djava.rmi.dgc.leaseValue=300000
	-Djava.rmi.server.hostname=213.114.146.8 \
	-Djava.rmi.server.randomIDs=true \
	-Djava.rmi.server.disableHttp=true"

CLASSPATH="classes:lib/jdom.jar:lib/martyr.jar:lib/oro.jar:lib/JSX1.0.7.4.jar"

exec java ${VMARGS} net.sf.drftpd.master.ConnectionManager
