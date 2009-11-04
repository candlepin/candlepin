Files in this directory define the datamodel for the Entitlement Proxy.

SQL files totally suck, especially when you are trying to maintain schema
for multiple database flavors. We set out to start with Hibernate, but
hbm2ddl is just not nearly as easy to work with as one would've hoped.

ActiveRecord is cool because you define your schema in Ruby. So we set
out to do something similar, define your schema in python which also
handles the different database flavors and a lot easier to read.

NEEDED to build
----------------
    python-psycopg2
    python-sqlalchemy
