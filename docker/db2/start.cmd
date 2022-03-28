@REM
@REM Copyright (C) 2015-2022 Philip Helger and contributors
@REM philip[at]helger[dot]com
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM         http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off
::docker run -itd --pull=always --rm --name mydb2 --privileged=true -p 50000:50000 -e LICENSE=accept -e DB2INST1_PASSWORD=password -e DBNAME=testdb -v db2:/database ibmcom/db2:latest
::docker run -itd --pull=always --restart=always --name mydb2 --privileged=true -p 50000:50000 --env-file env_list.txt -v db2:/database ibmcom/db2:latest
docker run -itd --pull=always --rm --name mydb2 --privileged=true -p 50000:50000 --env-file env_list.txt -v db2:/database ibmcom/db2:latest