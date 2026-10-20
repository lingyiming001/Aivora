from pathlib import Path
import os,subprocess,signal,time,shutil
r=Path(__file__).resolve().parents[1]
env=os.environ.copy()
env_file = r / 'deploy/.env'
if not env_file.exists():
 env_file = r / '.env'
for line in env_file.read_text().splitlines():
 if '=' in line and not line.lstrip().startswith('#'):
  k,v=line.split('=',1);env[k.strip()]=v.strip().strip('"').strip("'")
env['DATABASE_URL']='jdbc:postgresql://127.0.0.1:15432/aivora'
env['REDIS_HOST']='127.0.0.1';env['REDIS_PORT']='16379'
backend_pid=int(subprocess.run(['lsof','-tiTCP:8080','-sTCP:LISTEN'],capture_output=True,text=True).stdout.strip() or '0')
cmd=subprocess.run(['ps','-p',str(backend_pid),'-o','args='],capture_output=True,text=True).stdout
if 'aivora' in cmd.lower():
 os.kill(backend_pid,signal.SIGTERM)
 for i in range(30):
  try:os.kill(backend_pid,0);time.sleep(.2)
  except ProcessLookupError:break
runtime_jar=Path('/tmp')/('aivora-runtime-'+str(time.time_ns())+'.jar')
shutil.copyfile(r/'aivora/aivora-admin/target/aivora-admin-0.1.0.jar',runtime_jar)
with open('/tmp/aivora-current.log','ab') as log:
 p=subprocess.Popen(['/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java','-jar',str(runtime_jar)],cwd=r,env=env,stdout=log,stderr=log,start_new_session=True)
print('Started updated Aivora backend, PID',p.pid)
