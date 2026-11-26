### AiChat
  this app allows the user to talk to any llm, and run in an agent loop
* the user can send the file  and messaged etc and we might have to feed it to the agent
* we will maintain the state b/w backend and client, the client will send the chatId:String|null null means the new chat and string means perv one; after we done with a successful response we'll store the new message + response in the db and also stream it 
