<?php
//Rerouting Auth script, PHP version
//By Thulinma, Sep 1 2011
//
//Description:
//Changes the reply to the server from minecraft to always be "YES", allowing all users to login.
//A plugin can then check the login status afterwards, giving access to the named account or not.
//
//Usage:
//Put an entry in the hosts file on the (minecraft) server rerouting "www.minecraft.net" to the IP
//of the (web / HTTP) server this script is installed on.
//Make sure the folder this script is in is writeable to the webserver, or at least make a file
//called "logins.json" that is writeable to the webserver.
//Configure the webserver to serve this file when http://www.minecraft.net/game/checkserver.jsp is
//called.

//An example config excerpt for lighttpd:
/*
$HTTP["host"] =~ "(^|\.)minecraft.net$" {
  server.document-root = "/home/thulinma/minecraft/fakeserver"
  url.rewrite = ("^/game/checkserver.jsp(.*)$" => "/checkserver.php$1")
}
*/

//An example .htaccess file for Apache (thanks to maldiablo):
/*
Options +FollowSymlinks
RewriteEngine on
RewriteRule ^(.*)\.jsp$ $1.php [nc]
*/

//Instead of /home/thulinma/minecraft/fakeserver you would of course put the path where you saved
//this script yourself! The url rewrite will turn the jsp requests into PHP requests to this script.
//
//Server plugins can then call http://www.minecraft.net/game/checkserver.jsp?premium=[USERNAME HERE]
//and will get a single word as response to indicate if the user is premium or not.
//Responses: PREMIUM or NOTPREMIUM
//This check will work only once per login, so new users signing in with the same name afterwards are
//not seen as the same person unless they actually are the same person.



//Script starts here, you probably don't want to modify anything below here.


//Return script version if asked
if ($_REQUEST['mixver']){die("MIXV1");}

// Create the stream context (added by maldiablo)
$context = stream_context_create(array('http' => array('timeout' => 5)));

//Load the current waitinglist.
$log = json_decode(file_get_contents("logins.json"), true);

//Check a user in the waitinglist
if ($_REQUEST['premium']){
  if ($log[$_REQUEST['premium']] == "YES"){echo "PREMIUM";}else{echo "NOTPREMIUM";}
  unset($log[$_REQUEST['premium']]);//remove the user from the waitinglist
  file_put_contents("logins.json", json_encode($log));
  die();
  //not found? return the save answer (not premium) by default
  die("NOTPREMIUM");
}

//check for proper config, if called without variables.
if (!$_REQUEST['user'] && !$_REQUEST['serverId']){
  if ($_SERVER["HTTP_HOST"] == "session.minecraft.net"){
    die("The script is installed properly! Yay!");
  }else{
    die("Please add a line to your hosts file to reroute session.minecraft.net to the address of this server (localhost?)!");
  }
}

$log[$_REQUEST['user']] = "NO";

// We need to override the IP for session.minecraft.net because of the hosts file edits
// 184.73.166.45 is the IP for session.minecraft.net
$ch = curl_init();
$headers = array("Host: session.minecraft.net");
curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
curl_setopt($ch, CURLOPT_URL, "http://184.73.166.45/game/checkserver.jsp?user=".$_REQUEST['user']."&serverId=".$_REQUEST['serverId']);
curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 5);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
$response = curl_exec($ch);
file_put_contents("log.txt", "Checking: ".json_encode(Array("server" => "MCNET", "response" => $response))."\n", FILE_APPEND);
if ($response == "YES"){$log[$_REQUEST['user']] = "YES";}

//Store the auth status.
file_put_contents("logins.json", json_encode($log));
//Always return yes to the server - a plugin will further check it.
die("YES");
?>
