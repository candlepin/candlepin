drop user candlepin cascade;
create user candlepin identified by candlepin default tablespace users;
grant dba to candlepin;
quit
