package ca.polymtl.inf8405.sevenwonders
package app

import api._
import api.SevenWondersApi.Client

import org.apache.thrift.transport.TTransport
import org.apache.thrift.protocol.TBinaryProtocol
import java.net.InetAddress

import java.util.{ List => JList, Map => JMap }
import api.Resource
import akka.actor.{ActorSystem, TypedActor}
import scala.concurrent._
import java.util

object ApiHelper {
  type GameId = String
  type TCard = String
  type TPlayer = InetAddress
  type TTrade = JMap[Resource, JList[NeighborReference]]
}

trait GameClient extends SevenWondersApi.Iface {
  def disconnect()
  def username(): Future[String]
}

class GameClientImpl( transport: TTransport, val ip: InetAddress, lobby: GameLobby, dispatch: Dispatcher, system: ActorSystem ) extends GameClient
{
  import ApiHelper._
  import collection.JavaConversions._
  import system.dispatcher

  private val client = new Client( new TBinaryProtocol( transport ) )

  def s_listGamesRequest(geo: GeoLocation) {
    println("client request")
    lobby.list.foreach( l => {
      println(s"client list ${l.size}")
      client.c_listGamesResponse( l )
    })
  }

  def c_listGamesResponse(rooms: JList[GameRoom]) { }

  def s_create( definition: GameRoomDef ) {
    dispatch.create( definition, TypedActor.self ).foreach( g => game = Some(g) )
  }

  def s_join( id: GameId ) {
    lobby.join( id, TypedActor.self ).foreach( g => game = Some(g) )
  }

  def s_start() {
    game.foreach( _.start() )
  }

  def s_playCard( card: TCard, trade: TTrade ) {
    game.foreach( _.playCard( card, trade ) )
  }

  def s_playWonder( trade: TTrade ) {
    game.foreach( _.playWonder( trade ) )
  }
  def s_discard( card: TCard ) {
    game.foreach( _.discard( card ) )
  }

  def disconnect() {
    game.foreach( _.disconnect( TypedActor.self ) )
  }

  def s_pong() {
    dispatch.pong( ip )
  }

  def c_joined( user: String) {
    client.c_joined( user )
  }

  def c_connected(users: JList[String]) {
    println(s"client connected ${users.size}")
    client.c_connected(users)
  }

  def c_left(user: String) {
    client.c_left( user )
  }
  def c_begin(state: GameState) {
    client.c_begin( state )
  }
  def c_sendState(state: GameState) {
    client.c_sendState( state )
  }
  def c_sendEndState(state: GameState, detail: JList[JMap[String, Integer]]) {
    client.c_sendEndState( state, detail )
  }
  def c_ping() {
    client.c_ping()
  }

  def username() = future { ip.toString }

  private var game: Option[TGame] = None

  override def equals( other: Any ) = {
    other match {
      case a: GameClientImpl => a.ip == ip
      case _ => false
    }
  }

  override def hashCode = ip.hashCode
}