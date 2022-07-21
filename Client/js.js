var ws;
var playerCount = 0;
var players = [];
var permissionLevel = 0;
var submitted = 0;
var mystring;
var kickedGameStarted = 0;
var alertFocus = 0;
var state;

function connect() {
   var username = $('#username').val();
   var IP = $('#IP').val();
   var port = $('#port').val();

   if ("WebSocket" in window) {
      $("#connectmenudiv").hide();
      state = 0;
      try {
         // open websocket
         ws = new WebSocket(`ws://${IP}:${port}/CTD`);

         ws.onopen = function () {
            // Web Socket is connected, send data using send()
            ws.send(`clientConnect ${username}`);
            $("#playerlistdiv").show();
            $(".username").text(username);
            $(".usernamediv").show();
         };

         ws.onmessage = function (evt) {
            var receivedText = evt.data;
            var command = receivedText.slice(0, receivedText.indexOf(" "));
            var data = receivedText.slice(receivedText.indexOf(" ") + 1);
            if (command == "playerCount") {
               playerCount = parseInt(data);
               $(".playercount").text(playerCount);
            }
            if (command == "players") {
               players = data.split(" ")
               $(".playerlist").text("")
               for (playerindex = 0; playerindex < players.length; playerindex++) {
                  if (playerindex == 0) {
                     var listitem = '<li>' + players[playerindex] + ' (leader)</li>';
                  }
                  else {
                     var listitem = '<li>' + players[playerindex] + '</li>';
                  }
                  listitem = listitem.replace(/&nbsp;/g, " ");
                  $('.playerlist').append(listitem);
               }
            }
            if (command == "permissionLevel") {
               permissionLevel = parseInt(data);
               if (permissionLevel) {
                  $(".leadermsg").show();
                  $(".startbtn").show();
                  if (state > 1) {
                     $("#endgamediv").show();
                  }
                  else {
                     $("#endgamediv").hide();
                  }
               }
               else {
                  $(".leadermsg").hide();
                  $(".startbtn").hide();
                  $("#endgamediv").hide();
               }
            }
            if (command == "gameStarted") {
               bsalert("This game has already started!");
               kickedGameStarted = 1;
            }
            if (command == "gameStart") {
               state = 1;
               $("#playerlistdiv").hide();
               $("#enterstringdiv").show();
               $('#inputstring').val("");
               if (!alertFocus) {
                  $("#inputstring").focus();
               }
               if (permissionLevel) {
                  $("#endgamediv").show();
               }
            }
            if (command == "gameEnded") {
               if (data == "notEnoughPlayers") {
                  bsalert("Game has ended as there are no longer enough players.");
               }
               if (data == "leaderEnded") {
                  bsalert("Leader ended game.");
               }
               returnToPlayerList();
            }
            if (command == "submittedPlayers") {
               var submittedPlayers = parseInt(data);
               if (submitted) {
                  var remainingPlayers = playerCount - submittedPlayers;
                  $("#enterstringdiv").hide();
                  $("#submittedplayersdiv").show();
                  $(".waitingplayerscount").text(remainingPlayers);
                  if (remainingPlayers == 1) {
                     $(".waitingplayersplural").text("player");
                     $(".stringsplural").text("string");
                  }
                  else {
                     $(".waitingplayersplural").text("players");
                     $(".stringsplural").text("strings");
                  }
               }
            }
            if (command == "guessStart") {
               state = 2;
               $("#submittedplayersdiv").hide();
               $("#gamemaindiv").show();
               $('#inputguess').val("");
               if (permissionLevel) {
                  $(".gamesubmitbtn").show();
               }
               else {
                  $(".gamesubmitbtn").hide();
               }
               if (!alertFocus) {
                  $("#inputguess").focus();
               }
            }
            if (command == "explainer") {
               $(".gamemain-explainer").text(data);
            }
            if (command == "guesser") {
               $(".gamemain-guesser").text(data);
            }
            if (command == "string") {
               $("#gamemain-string").text(data);
            }
            if (command == "playerLeave") {
               var guesserChanged;
               var explainerChanged;
               var stringChanged;
               var answerChanged;
               changeData = data.slice(0, data.indexOf(" "));
               leaverName = data.slice(data.indexOf(" ") + 1);
               for (charindex = 0; charindex < data.length; charindex++) {
                  char = changeData.charAt(charindex);
                  if (charindex == 0) {
                     if (char == "0") {
                        guesserChanged = false;
                     }
                     if (char == "1") {
                        guesserChanged = true;
                     }
                  }
                  if (charindex == 1) {
                     if (char == "0") {
                        explainerChanged = false;
                     }
                     if (char == "1") {
                        explainerChanged = true;
                     }
                  }
                  if (charindex == 2) {
                     if (char == "0") {
                        stringChanged = false;
                     }
                     if (char == "1") {
                        stringChanged = true;
                     }
                  }
                  if (charindex == 3) {
                     if (char == "0") {
                        answerChanged = false;
                     }
                     if (char == "1") {
                        answerChanged = true;
                     }
                  }
               }
               
               if (!answerChanged && !explainerChanged && !stringChanged && !guesserChanged) {
                  bsinfo(`${leaverName} has left the game. However, this does not affect you.`)
               }
               else {
                  var changedReadable = [];
                  if (answerChanged) {
                     changedReadable.push("The string you were inputting");
                  }
                  if (explainerChanged) {
                     changedReadable.push("The person who was giving you your string to input");
                  }
                  if (stringChanged) {
                     changedReadable.push("The string you were reading out");
                  }
                  if (guesserChanged) {
                     changedReadable.push("The person you were explaining your string to");
                  }
                  var alertMessage = `Due to ${leaverName} leaving, the game had to be re-shuffled. The following have changed: <br>`;
                  alertMessage += "<ul>";
                  for (i = 0; i < changedReadable.length; i++) {
                     var listitem = '<li>' + changedReadable[i] + '</li>';
                     alertMessage += listitem;
                  }
                  alertMessage += "</ul>";

                  bsalert(alertMessage);
               }
            }
            if (command == "requestGuess") {
               var myguess = $('#inputguess').val();
               ws.send(`submitGuess ${myguess}`);
            }
            if (command == "incorrect") {
               bsalert("One or more answers is/are incorrect! Check your answers through again, and try again.");
            }
            if (command == "correct") {
               $("#gamemaindiv").hide();
               $("#endscreendiv").show();
               setTimeout(returnToPlayerList, 5000);
            }
         };

         ws.onclose = function () {
            // websocket is closed.
            if (kickedGameStarted) {
               kickedGameStarted = 0;
            }
            else {
               bsalert("Disconnected");
            }
            gamereset();
            state = undefined;
         };
      }
      catch (e) {
         bsalert("Invalid IP or port");
         gamereset();
      }
   } else {
      bsalert("Websocket is not supported by your browser :c");
   }
}

function gamereset() {
   ws = undefined;
   playerCount = 0;
   players = []
   permissionLevel = 0;
   submitted = 0;
   $("#endgamediv").hide();
   $("#enterstringdiv").hide();
   $("#playerlistdiv").hide();
   $(".usernamediv").hide();
   $("#submittedplayersdiv").hide();
   $("#connectmenudiv").show();
   $("#gamemaindiv").hide();
   $("#endscreendiv").hide();
};

function returnToPlayerList() {
   state = 1;
   submitted = 0;
   $("#endgamediv").hide();
   $("#enterstringdiv").hide();
   $("#submittedplayersdiv").hide();
   $("#gamemaindiv").hide();
   $("#playerlistdiv").show();
   $("#endscreendiv").hide();
}

function startbtnclick() {
   if (playerCount < 3) {
      bsalert("3 or more players are required to start!");
   }
   else {
      ws.send("requestStart null");
   }
};

function submitstring() {
   submitted = 1;
   mystring = $('#inputstring').val()
   ws.send(`submitString ${mystring}`);
};

function endgame() {
   if (window.confirm("Are you sure you want to end the game?")) {
      ws.send("endGame null");
   }
};

function onload() {
   $("#username").focus();
};

function replaceillegalchars(e) {
   var validChars = [' ', 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '~', '`', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+', '[', '{', ']', '}', '\\', '|', ';', ':', "'", '"', ',', '<', '.', '>', '/', '?'];
   var valid = "";
   for (var char = 0; char < e.value.length; char++) {
      if (validChars.includes(e.value[char])) {
         valid += e.value[char];
      }
      else {
         $(".illegalchar").val(valid);
      }
   }
};

function bsalert(text) {
   $("#alertbox").addClass("show");
   $("#alerttext").html(text);
   $("#alertclosebtn").focus();
   alertFocus = 1;
};

function bsinfo(text) {
   var toast = document.getElementById("infobox");
   var bstoast = new bootstrap.Toast(toast);
   bstoast.show();
   $("#infotext").html(text);
};

function helpalert() {
   $("#helpbox").addClass("show");
   $("#helpclosebtn").focus();
   alertFocus = 1;
};

function alertfocuscancel() {
   alertFocus = 0;
   if (state == undefined) {
      $("#username").focus();
   }
   if (state == 1) {
      $("#inputstring").focus();
   }
   if (state == 2) {
      $("#inputguess").focus();
   }
};


function closehelp() {
   $("#helpbox").removeClass("show");
   alertfocuscancel();
};

function closealert() {
   $("#alertbox").removeClass("show");
   alertfocuscancel();
};

function gamesubmitbtnclick() {
   ws.send("checkAnswers null");
};