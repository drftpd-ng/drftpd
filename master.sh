export VMARGS="
	-server
	-Djava.library.path=lib \
	-Djava.rmi.dgc.leaseValue=300000 \
	-Djava.rmi.server.randomIDs=true \
	-Djava.rmi.server.disableHttp=true \
	-Djava.rmi.server.hostname=wfa.mine.nu"

export CLASSPATH="classes:lib/jdom.jar:lib/martyr.jar:lib/oro.jar:lib/JSX1.0.7.4.jar:lib/replacer.jar"

exec java ${VMARGS} $@ net.sf.drftpd.master.ConnectionManager 2>&1 | tee drftpd.out
