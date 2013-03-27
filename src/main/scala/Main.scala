package com.github.jedesah

import util.Random
import utils.{Math, Utils}
import Utils._
import collection.MultiMap
import collection.MultiSet
import collection.conversions._

object SevenWonders 
{

  def beginGame( nbPlayers: Int ): Game = {
    val cards = classicSevenWonders.generateCards(nbPlayers)
    val chosenCivilizations = Random.shuffle(civilizations.toList).take(nbPlayers)
    val players = chosenCivilizations.map{
      civ =>
        Player(MultiSet(), 3, MultiSet(), Set(), civ)
    }
    Game(players, cards, MultiSet()).beginAge()
  }

  case class Cost(coins: Int, resources: MultiSet[Resource]) {
    def this(resources: MultiSet[Resource]) = this(0, resources)
    def this(coins: Int) = this(coins, MultiSet())
  }
  object Free extends Cost(0)

  class Card( 
    val name: String,
    val cost: Cost,
    val evolutions: Set[Card]
  )
  {
    /**
     * Implements the effect of the card on the game.
     * Default implementation: the card has no effect.
     * Most cards should have no effect like a resource card, military card, etc.
     * But commerce reward coins and maybe one day city cards have an immediate effect on the game
     * which should be implemented in this method that will be called after a card has been played
     * @param game The current state of the game
     * @param playedBy The player who played this card
     * @return The new game after resolving the card
     */
    def resolve(game: Game, playedBy: Player): Game = game
  }

  trait ScienceValue {
    def +(other: ScienceValue): ScienceValue = other match {
      case other: SimpleScienceValue => this + other
      case other: OptionalScienceValue => this + other
    }
    def +(other: SimpleScienceValue): ScienceValue
    def +(other: OptionalScienceValue): OptionalScienceValue
    def |(other: ScienceValue): ScienceValue = other match {
      case other: SimpleScienceValue => this | other
      case other: OptionalScienceValue => this | other
    }
    def |(other: SimpleScienceValue): ScienceValue
    def |(other: OptionalScienceValue): OptionalScienceValue
    def victoryPointValue: Int
  }
  case class SimpleScienceValue(compass: Int, gear: Int, tablet: Int) extends ScienceValue {
    def +(other: SimpleScienceValue) = SimpleScienceValue(compass + other.compass, gear + other.gear, tablet + other.tablet)
    def +(other: OptionalScienceValue) = other + this
    def |(other: SimpleScienceValue) =
      if (other != this)
        OptionalScienceValue(Set(this, other))
      else
        this
    def |(other: OptionalScienceValue) = other | this
    def victoryPointValue = {
      val setValue = List(compass, gear, tablet).min * 7
      val stackValue = List(compass, gear, tablet).map(Math.pow(_, 2)).sum
      setValue + stackValue
    }
  }
  case class OptionalScienceValue(alternatives: Set[ScienceValue]) extends ScienceValue {
    def +(other: SimpleScienceValue) = OptionalScienceValue(alternatives.map(_ + other))
    def +(other: OptionalScienceValue) = {
      val newAlternatives = for {
        alt1 <- alternatives;
        alt2 <- other.alternatives
      } yield alt1 + alt2
      OptionalScienceValue(newAlternatives.toSet)
    }
    override def |(other: ScienceValue): OptionalScienceValue = other match {
      case other: SimpleScienceValue => this | other
      case other: OptionalScienceValue => this | other
    }
    def |(other: SimpleScienceValue): OptionalScienceValue = OptionalScienceValue(alternatives + other)
    def |(other: OptionalScienceValue) =
      alternatives.foldLeft[OptionalScienceValue](other){(other, alternative) => other | alternative}
    def victoryPointValue = alternatives.map(_.victoryPointValue).max
  }

  trait HasScience {
    val value: ScienceValue
  }

  val compass = SimpleScienceValue(1, 0, 0)
  val gear = SimpleScienceValue(0, 1, 0)
  val tablet = SimpleScienceValue(0, 0, 1)

  trait ProductionCard {
    val prod: Production
  }

  case class ScienceCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    override val value: ScienceValue
  ) extends Card( name, cost, evolutions ) with HasScience

  case class MilitaryCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    value: Int
  ) extends Card( name, cost, evolutions )

  class CommercialCard(
    name: String,
    cost: Cost,
    evolutions: Set[Card]
  ) extends Card( name, cost, evolutions )

  case class RebateCommercialCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    affectedResources: Set[Resource],
    fromWho: Set[NeighboorReference]
  ) extends CommercialCard( name, cost, evolutions )

  case class ProductionCommercialCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    prod: Production
  ) extends CommercialCard( name, cost, evolutions ) with ProductionCard

  case class RewardCommercialCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    coinReward: Option[Reward],
    victoryPointReward: Option[ComplexReward]
  ) extends CommercialCard( name, cost, evolutions )
  {
    override def resolve(game: Game, playedBy: Player): Game = {
      coinReward match {
        case None => game
        case Some(reward) =>
          val rewardAmount = reward match {
            case SimpleReward(amount) => amount
            case reward: ComplexReward => playedBy.calculateRewardAmount(reward, game.getNeighboorsCards(playedBy))
          }
          val newCoinsAmount = playedBy.coins + rewardAmount
          game.copy(players = game.players.updated(game.players.indexOf(playedBy), playedBy.copy(coins = newCoinsAmount)))
      }
    }
  }

  class ResourceCard(
    name: String,
    cost: Cost,
    val prod: Production
  ) extends Card(name, cost, Set() ) with ProductionCard

  case class RawMaterialCard(
    override val name: String,
    override val cost: Cost,
    production: Production
  ) extends ResourceCard(name, cost, production)

  case class ManufacturedGoodCard(
    override val name: String,
    override val cost: Cost,
    production: Production
  ) extends ResourceCard(name, cost, production) {
    def this(name: String, production: Production) = this(name, Cost(0, MultiSet()), production)
  }
  
  case class CivilianCard(
    override val name: String,
    override val cost: Cost,
    override val evolutions: Set[Card],
    amount: Int
  ) extends Card( name, cost, evolutions )

  class GuildCard(name: String, cost: Cost) extends Card(name, cost, Set())

  case class VictoryPointsGuildCard(
    override val name: String,
    override val cost: Cost,
    victoryPoint:ComplexReward
  ) extends GuildCard(name, cost)

  trait Reward
  case class SimpleReward(amount: Int) extends Reward
  case class ComplexReward(
    amount: Int,
    forEach: Class[_ <: Card],
    from: Set[PlayerReference]
  ) extends Reward

  sealed trait Resource
  sealed trait RawMaterial extends Resource
  sealed trait ManufacturedGood extends Resource
  object Clay extends RawMaterial {
    override def toString = "Clay"
  }
  object Wood extends RawMaterial {
    override def toString = "Wood"
  }
  object Ore extends RawMaterial {
    override def toString = "Ore"
  }
  object Stone extends RawMaterial {
    override def toString = "Stone"
  }
  object Glass extends ManufacturedGood {
    override def toString = "Glass"
  }
  object Paper extends ManufacturedGood {
    override def toString = "Paper"
  }
  object Tapestry extends ManufacturedGood {
    override def toString = "Tapestry"
  }

  sealed trait PlayerReference
  sealed trait NeighboorReference extends PlayerReference
  object Left extends NeighboorReference
  object Right extends NeighboorReference
  object Self extends PlayerReference

  trait Production {
    def consume(resources: MultiSet[Resource]): Set[MultiSet[Resource]]
    def consumes(resource: Resource): Boolean
    def -(resource: Resource): Production
    def +(other: Production): Production = other match {
      case other: OptionalProduction => this + other
      case other: CumulativeProduction => this + other
    }
    def +(other: CumulativeProduction): Production
    def +(other: OptionalProduction): OptionalProduction
    def |(other: Production): Production = other match {
      case other: OptionalProduction => this | other
      case other: CumulativeProduction => this | other
    }
    def |(other: CumulativeProduction): Production
    def |(other: OptionalProduction): OptionalProduction
  }
  case class OptionalProduction(possibilities: Set[CumulativeProduction]) extends Production {
    def consume(resources: MultiSet[Resource]): Set[MultiSet[Resource]] = possibilities.map(_.consume(resources).head)
    def consumes(resource: Resource) = possibilities.exists(_.consumes(resource))
    def -(resource: Resource) = OptionalProduction(possibilities.map(_ - resource))
    def +(other: OptionalProduction): OptionalProduction =
      OptionalProduction(possibilities.map( poss1 => other.possibilities.map( poss2 => poss1 + poss2)).flatten)
    def +(other: CumulativeProduction): OptionalProduction = OptionalProduction(possibilities.map(_ + other))
    def |(other: OptionalProduction): OptionalProduction =
      OptionalProduction(possibilities ++ other.possibilities)
    def |(other: CumulativeProduction): OptionalProduction = OptionalProduction(possibilities + other)
  }
  case class CumulativeProduction(produces: MultiSet[Resource]) extends Production {
    def this(resource: Resource) = this(MultiSet(resource))
    def consume(resources: MultiSet[Resource]): Set[MultiSet[Resource]] = Set(resources -- produces)
    def consumes(resource: Resource) = produces.contains(resource)
    def -(resource: Resource) = CumulativeProduction(produces - resource)
    def +(other: OptionalProduction) = other + this
    def +(other: CumulativeProduction) = CumulativeProduction(produces ++ other.produces)
    def |(other: OptionalProduction) = other | this
    def |(other: CumulativeProduction) = OptionalProduction(Set(this, other))
  }

  implicit def ResourceToProduction(value: Resource) = new CumulativeProduction(value)

  type Trade = MultiMap[Resource, NeighboorReference]

  case class Player(hand: MultiSet[Card], coins: Int, battleMarkers: MultiSet[BattleMarker], played: Set[Card], civilization: Civilization) {
    def discard(card: Card): Player = Player(hand - card, coins + 3, battleMarkers, played, civilization)

    /**
     * Handles all state changing relative to this player when he plays a card.
     * @param card The card to play
     * @param trade The trade used to play this card. Can be an empty trade
     * @return The updated Player state along with the amount of coins given to the left and right players
     */
    def play(card: Card, trade: Trade): (Player, Map[NeighboorReference, Int]) = {
      val coinsMap = trade.values.toSet.map(ref => (ref, cost(trade, ref))).toMap
      val player = Player(hand - card, coins - cost(trade) - card.cost.coins, battleMarkers, played + card, civilization)
      (player, coinsMap)
    }

    def playableCards(availableThroughTrade: Map[NeighboorReference, Production]): Set[Card] =
      hand.toSet.filter( card => canPlayCard(card, availableThroughTrade))

    def totalProduction: Production = {
      val productionCards = played.filter(_.isInstanceOf[ProductionCard]).map(_.asInstanceOf[ProductionCard])
      productionCards.foldLeft(civilization.base)((prod, card) => prod + card.prod)
    }

    def tradableProduction: Production = {
      val productionCards = played.filter(_.isInstanceOf[ResourceCard]).map(_.asInstanceOf[ResourceCard])
      productionCards.foldLeft(civilization.base)((prod, card) => prod + card.prod)
    }

    def militaryStrength: Int = played.filter(_.isInstanceOf[MilitaryCard]).map(_.asInstanceOf[MilitaryCard]).map(_.value).sum

    def score(neightboorCards: Map[NeighboorReference, Set[Card]]): Int =
      scienceScore + militaryScore + civilianScore + commerceScore(neightboorCards) + guildScore(neightboorCards)

    def scienceScore: Int = {
      val cardsWithScience: Traversable[HasScience] = played.filter(_.isInstanceOf[HasScience]).map(_.asInstanceOf[HasScience])
      val scienceValue = cardsWithScience.foldLeft[ScienceValue](SimpleScienceValue(0, 0, 0)){(scienceValue, card) => scienceValue + card.value}
      scienceValue.victoryPointValue
    }

    def militaryScore = battleMarkers.map(_.vicPoints).sum

    def civilianScore = {
      val civilianCards = played.filter(_.isInstanceOf[CivilianCard]).map(_.asInstanceOf[CivilianCard])
      // n.b. We need to convert the set to a multiset or else we would lose some information as some
      // cards can have the same victory point value
      civilianCards.toMultiSet.map(_.amount).sum
    }

    def commerceScore(neightboorCards: Map[NeighboorReference, Set[Card]]): Int = {
      val commerceVicPointCards = played.filter(_.isInstanceOf[RewardCommercialCard]).map(_.asInstanceOf[RewardCommercialCard])
      commerceVicPointCards.map {
        card =>
          card.victoryPointReward match {
            case None => 0
            case Some(vicPointReward) => calculateRewardAmount(vicPointReward, neightboorCards)
          }
      }.sum
    }

    def guildScore(neightboorCards: Map[NeighboorReference, Set[Card]]): Int = {
      val guildCards = played.filter(_.isInstanceOf[VictoryPointsGuildCard]).map(_.asInstanceOf[VictoryPointsGuildCard])
      guildCards.map{ card => calculateRewardAmount(card.victoryPoint, neightboorCards)}.sum
    }

    def calculateRewardAmount(reward: ComplexReward, neightboorCards: Map[NeighboorReference, Set[Card]]): Int = {
      val fromNeighboors = reward.from.filter(_.isInstanceOf[NeighboorReference]).map(_.asInstanceOf[NeighboorReference])
      val referencedNeighboorCards: MultiSet[Card] = fromNeighboors.map(neightboorCards(_).toMultiSet).reduce(_ ++ _)
      val referencedMyCards = if (reward.from.contains(Self)) played.toMultiSet else MultiSet()
      val cards = referencedNeighboorCards ++ referencedMyCards
      cards.map( card => if (card.getClass == reward.forEach) reward.amount else 0).sum
    }

    def canPlayCard(card: Card, availableThroughTrade: Map[NeighboorReference, Production]): Boolean = {
      !played.contains(card) && // You cannot play a card you already own
      (availableEvolutions.contains(card) || // You can play an evolution whether you can pay it's cost or not
      possibleTrades(card, availableThroughTrade).nonEmpty)
    }

    def possibleTrades(card: Card, tradableProduction: Map[NeighboorReference, Production]): Set[Trade] =
      possibleTradesWithoutConsideringCoins(card, tradableProduction).filter(cost(_) <= coins - card.cost.coins)

    def possibleTradesWithoutConsideringCoins(card: Card, tradableProduction: Map[NeighboorReference, Production]): Set[Trade] =
      totalProduction.consume(card.cost.resources).map(possibleTrades(_, tradableProduction)).flatten

    def possibleTrades(resources: MultiSet[Resource],
                       tradableResources: Map[NeighboorReference, Production]
                      ): Set[Trade] = {
      if (resources.isEmpty) Set(MultiMap[Resource, NeighboorReference]())
      else
        (
          for ((neighboorRef, production) <- tradableResources) yield
            if (production.consumes(resources.head)) {
              val subTrades: Set[Trade] = possibleTrades(resources.tail, tradableResources.updated(neighboorRef, production - resources.head))
              subTrades.map(trade => trade + (resources.head -> neighboorRef))
            }
            else
              Set.empty[Trade]
        ).flatten.toSet
    }

    /**
     * @param trade
     * @return The cost in coins of this trade
     */
    def cost(trade: Trade): Int =
      if (trade.isEmpty) 0
      else {
        val (resource, from) = trade.head
        cost(resource, from) + cost(trade.tail)
      }

    /**
     *
     * @param trade
     * @param from
     * @return The cost in coins of this trade related to the specified neighboor
     */
    def cost(trade: Trade, from: NeighboorReference): Int =
      if (trade.isEmpty) 0
      else {
        val (resource, from1) = trade.head
        if (from1 == from) cost(resource, from) else 0 + cost(trade.tail, from)
      }

    def cost(resource: Resource, from: NeighboorReference): Int = {
      val rebateCards: Traversable[RebateCommercialCard] =
        played.filter(_.isInstanceOf[RebateCommercialCard]).map(_.asInstanceOf[RebateCommercialCard])
      rebateCards.find(_.fromWho == from) match {
        case Some(rebateCard) => if (rebateCard.affectedResources.contains(resource)) 1 else 2
        case None => 2
      }
    }

    def availableEvolutions: Set[Card] = played.map(_.evolutions).flatten

    def +(delta: PlayerDelta) =
      this.copy(coins = coins + delta.coinDelta, played = played ++ delta.newCards, battleMarkers = battleMarkers ++ delta.newBattleMarkers)

    def -(previous: Player): PlayerDelta =
      PlayerDelta(played -- previous.played, coins - previous.coins, battleMarkers -- previous.battleMarkers)
  }

  type Age = Int

  case class Game(players: List[Player], cards: Map[Age, MultiSet[Card]], discarded: MultiSet[Card]) {
    def getNeighboors(player: Player): Set[Player] =
      Set(getLeftNeighboor(player), getRightNeighboor(player))

    def getLeftNeighboor(player: Player): Player = {
      val index = players.indexOf(player)
      players.shiftRight(index)
    }

    def getRightNeighboor(player: Player): Player = {
      val index = players.indexOf(player)
      players.shiftLeft(index)
    }

    def getNeighboorsCards(player: Player): Map[NeighboorReference, Set[Card]] = {
      Map(Left -> getLeftNeighboor(player).played, Right -> getRightNeighboor(player).played)
    }

    def playTurn(actions: Map[Player, Action]): Game = {
      val deltas = for ( (player, action) <- actions) yield {
        action match {
          case DiscardAction(card) =>
            GameDelta(Map(player -> player.discard(card).-(player)), MultiSet(card))
          case PlayAction(card, trade) => {
            val (newPlayer, coinsToGive) = player.play(card, trade)
            val left = getLeftNeighboor(player)
            val right = getLeftNeighboor(player)
            val leftDelta = PlayerDelta(Set(), coinsToGive.getOrElse(Left, 0), MultiSet())
            val rightDelta = PlayerDelta(Set(), coinsToGive.getOrElse(Right, 0), MultiSet())
            GameDelta(Map(player -> newPlayer.-(player), left -> leftDelta, right -> rightDelta), MultiSet())
          }

        }
      }
      val newGameState = this + deltas.reduce(_ + _)

      // We go through every played card and resolve it's effect (add coins to players who played a card that rewards in coins)
      val gameStateAfterResolvingCards = actions.foldLeft(newGameState) {
        (gameState, keyValue) =>
          keyValue match {
            case (player, PlayAction(card, trade)) => card.resolve(gameState, player)
            case _ => gameState
          }
      }

      // A list containning everyone's hand without the currently played card
      val hands = players.map(player => player.hand - actions(player).card)
      // A list of players with their upcomming hand
      val nextTurnPlayers = players.zip(if (currentAge == 1 || currentAge == 3) hands.shiftLeft else hands.shiftRight).map{
        case (player, hand) =>
          player.copy(hand = hand)
      }

      val finalGameState = gameStateAfterResolvingCards.copy(players = nextTurnPlayers)

      // Was this the last turn of this age?
      if (finalGameState.players.head.hand.size == 1){
        // Was this the last age?
        if (currentAge == 3)
          finalGameState.endAge()
        else
          finalGameState.endAge().beginAge()
      }
      else
        finalGameState
    }

    def currentAge = cards.keys.toList.reverse.find(cards(_).isEmpty).getOrElse(0)

    def beginAge(): Game = {
      val shuffledNextAgeCards = Random.shuffle(cards(currentAge + 1).toList)
      val hands: List[MultiSet[Card]] = shuffledNextAgeCards.grouped(7).toList.map(_.toMultiSet)
      val updatedPlayers: List[Player] = players.zip(hands).map{ case (player, hand) => player.copy(hand = hand) }
      Game(updatedPlayers, cards.updated(currentAge, MultiSet()), discarded)
    }

    def endAge(): Game = {
      val winMarker = currentAge match { case 1 => VictoryBattleMarker(1) case 2 => VictoryBattleMarker(3) case 3 => VictoryBattleMarker(5)}
      val playerDeltas = players.createMap{
        player =>
          val leftPlayer = getLeftNeighboor(player)
          val wonLeft = player.militaryStrength > leftPlayer.militaryScore
          val tieLeft = player.militaryStrength == leftPlayer.militaryStrength
          val leftBattleMarker =
            if (tieLeft) MultiSet[BattleMarker]()
            else if (wonLeft) MultiSet(winMarker)
            else MultiSet(new DefeatBattleMarker)

          val rightPlayer = getLeftNeighboor(player)
          val wonRight = player.militaryStrength > rightPlayer.militaryStrength
          val tieRight = player.militaryStrength == rightPlayer.militaryStrength
          val rightBattleMarker =
            if (tieRight) MultiSet()
            else if (wonRight) MultiSet(winMarker)
            else MultiSet(new DefeatBattleMarker)

          PlayerDelta(Set(), 0, leftBattleMarker ++ rightBattleMarker)
      }
      val discards = players.map(_.hand).reduce(_ ++ _)
      this + GameDelta(playerDeltas, discards)
    }

    def +(delta: GameDelta): Game = {
      val updatedPlayers = players.map{
        player =>
          val playerDelta = delta.playerDeltas(player)
          player.copy(coins = player.coins + playerDelta.coinDelta, played = player.played ++ playerDelta.newCards)
      }
      Game(updatedPlayers, cards, discarded ++ delta.additionalDiscards)
    }
  }

  case class GameDelta(playerDeltas: Map[Player, PlayerDelta], additionalDiscards: MultiSet[Card]) {
    def +(other: GameDelta): GameDelta = {
      val newPlayerDeltas: Map[Player, PlayerDelta] = playerDeltas.map{case (player, delta) => (player, other.playerDeltas(player) + delta)}
      val totalDiscards = additionalDiscards ++ other.additionalDiscards
      GameDelta(newPlayerDeltas, totalDiscards)
    }
    def +(player: Player, other: PlayerDelta): GameDelta =
      GameDelta(playerDeltas.updated(player, playerDeltas(player) + other), additionalDiscards)
  }

  case class PlayerDelta(newCards: Set[Card], coinDelta: Int, newBattleMarkers: MultiSet[BattleMarker]) {
    def +(other: PlayerDelta): PlayerDelta =
      PlayerDelta(newCards ++ other.newCards, coinDelta + other.coinDelta, newBattleMarkers ++ other.newBattleMarkers)
  }

  class Action(val card: Card)
  case class PlayAction(override val card: Card, trade: Trade) extends Action(card)
  case class DiscardAction(override val card: Card) extends Action(card)

  class BattleMarker(val vicPoints: Int)
  class DefeatBattleMarker extends BattleMarker(-1)
  case class VictoryBattleMarker(override val vicPoints: Int) extends BattleMarker(vicPoints)

  type PlayerAmount = Int

  case class GameSetup(allCards: Map[Age, Map[PlayerAmount, MultiSet[Card]]], guildCards: Set[GuildCard]) {
    def generateCards(nbPlayers: Int): Map[Age, MultiSet[Card]] = {
      if (nbPlayers < 3) throw new IllegalArgumentException("You cannot currently play less than three players")
      else {
        // Adding all cards that should be used depending on the amount of players
        val cardsWithoutGuilds =
          allCards.mapValues( cards => (3 to nbPlayers).foldLeft(MultiSet[Card]())((set, key) => set ++ cards(key)))
        // Add 2 + nbPlayers guild cards selected randomly
        cardsWithoutGuilds.updated(3, cardsWithoutGuilds(3).++[Card](setToMultiSet(AugmentedSet(guildCards).takeRandom(nbPlayers + 2))))
      }
    }
  }

  case class Civilization(name: String, base:Production)

  ////
  // AGE I
  ////

  // Commercial Cards
  val TAVERN = RewardCommercialCard("TAVERN", Free, Set(), Some(SimpleReward(5)), None)
  val WEST_TRADING_POST = RebateCommercialCard("WEST TRADING POST", Free, Set(FORUM), Set(Clay, Stone, Wood, Ore), Set(Left))
  val MARKETPLACE = RebateCommercialCard("MARKETPLACE", Free, Set(CARAVANSERY), Set(Glass, Tapestry, Paper), Set(Left, Right))
  val EAST_TRADING_POST = RebateCommercialCard("EAST TRADING POST", Free, Set(FORUM), Set(Clay, Stone, Wood, Ore), Set(Right))

  // Military Cards
  val STOCKADE = MilitaryCard("STOCKADE", Cost(0, MultiSet(Wood)), Set(), 1)
  val BARRACKS = MilitaryCard("BARRACKS", Cost(0, MultiSet(Ore)), Set(), 1)
  val GUARD_TOWER = MilitaryCard("GUARD TOWER", Cost(0, MultiSet(Clay)), Set(), 1)

  // Science Cards
  val WORKSHOP = ScienceCard("WORKSHOP", Cost(0, MultiSet(Glass)), Set(LABORATORY, ARCHERY_RANGE), gear)
  val SCRIPTORIUM = ScienceCard("SCRIPTORIUM", Cost(0, MultiSet(Paper)), Set(COURTHOUSE, LIBRARY), tablet)
  val APOTHECARY = ScienceCard("APOTHECARY", Cost(0, MultiSet(Tapestry)), Set(STABLES, DISPENSARY), compass)


  // Civilian Cards
  val THEATER = CivilianCard("THEATER", Free, Set(STATUE), 2)
  val BATHS = CivilianCard("BATHS", Cost(0, MultiSet(Stone)), Set(AQUEDUCT), 3)
  val ALTAR = CivilianCard("ALTAR", Free, Set(TEMPLE), 2)
  val PAWNSHOP = CivilianCard("PAWNSHOP", Free, Set(), 3)

  // Raw Material Cards
  val TREE_FARM = new RawMaterialCard("TREE FARM", new Cost(1), Wood | Clay)
  val MINE = new RawMaterialCard("MINE", new Cost(1), Stone | Ore)
  val CLAY_PIT = new RawMaterialCard("CLAY PIT",new Cost(1), Clay | Ore)
  val TIMBER_YARD = new RawMaterialCard("TIMBER YARD", new Cost(1), Stone | Wood)
  val STONE_PIT = new RawMaterialCard("STONE PIT", Free, Stone)
  val FOREST_CAVE = new RawMaterialCard("FOREST CAVE", new Cost(1), Wood | Ore)
  val LUMBER_YARD = new RawMaterialCard("LUMBER YARD", Free, Wood)
  val ORE_VEIN = new RawMaterialCard("ORE VEIN", Free, Ore)
  val EXCAVATION = new RawMaterialCard("EXCAVATION", new Cost(1), Stone | Clay)
  val CLAY_POOL = new RawMaterialCard("CLAY POOL", Free, Clay)

  // Manufactured Good Cards
  val LOOM = new ManufacturedGoodCard("LOOM", Tapestry)
  val GLASSWORKS = new ManufacturedGoodCard("GLASSWORKS", Glass)
  val PRESS = new ManufacturedGoodCard("PRESS", Paper)

  ////
  // AGE II
  ////

  // Commercial Cards
  val CARAVANSERY = ProductionCommercialCard("CARAVANSERY", Cost(0, MultiSet(Wood, Wood)), Set(LIGHTHOUSE), Wood | Stone | Ore | Clay)
  val FORUM = ProductionCommercialCard("FORUM", Cost(0, MultiSet(Clay, Clay)), Set(HAVEN), Glass | Tapestry | Paper)
  val BAZAR = RewardCommercialCard("BAZAR", Free, Set(), Some(ComplexReward(2, classOf[ManufacturedGoodCard], Set(Left, Self, Right))), None)
  val VINEYARD = RewardCommercialCard("VINEYARD", Free, Set(), Some(ComplexReward(1, classOf[RawMaterialCard], Set(Left, Self, Right))), None)

  // Military Cards
  val WALLS = MilitaryCard("WALLS", Cost(0, MultiSet(Stone, Stone, Stone)), Set(FORTIFICATIONS), 2)
  val ARCHERY_RANGE = MilitaryCard("ARCHERY RANGE", Cost(0, MultiSet(Wood, Wood, Ore)), Set(), 2)
  val TRAINING_GROUND = MilitaryCard("TRAINING GROUND", Cost(0, MultiSet(Ore, Ore, Wood)), Set(CIRCUS), 2)
  val STABLES = MilitaryCard("STABLES", Cost(0, MultiSet(Clay, Wood, Ore)), Set(), 2)

  // Science Cards
  val SCHOOL = ScienceCard("SCHOOL", Cost(0, MultiSet(Wood, Paper)), Set(ACADEMY, STUDY), tablet)
  val LIBRARY = ScienceCard("LIBRARY", Cost(0, MultiSet(Stone, Stone, Tapestry)), Set(SENATE, UNIVERSITY), tablet)
  val LABORATORY = ScienceCard("LABORATORY", Cost(0, MultiSet(Clay, Clay, Paper)), Set(OBSERVATORY, SIEGE_WORKSHOP),gear)
  val DISPENSARY = ScienceCard("DISPENSARY", Cost(0, MultiSet(Ore, Ore, Glass)), Set(LODGE, ARENA), compass)

  // Civilian Cards
  val AQUEDUCT = CivilianCard("AQUEDUC", Cost(0, MultiSet(Stone, Stone, Stone)), Set(), 5)
  val STATUE = CivilianCard("STATUE", Cost(0, MultiSet(Ore, Ore, Wood)), Set(GARDENS), 4)
  val TEMPLE = CivilianCard("TEMPLE", Cost(0, MultiSet(Wood, Clay, Glass)), Set(PANTHEON), 3)
  val COURTHOUSE = CivilianCard("COURTHOUSE", Cost(0, MultiSet(Clay, Clay, Tapestry)), Set(), 4)

  // Raw Material Cards
  val FOUNDRY = new RawMaterialCard("FOUNDRY", new Cost(1), Ore + Ore)
  val QUARRY = new RawMaterialCard("QUARRY", new Cost(1), Stone + Stone)
  val BRICKYARD = new RawMaterialCard("BRICKYARD", new Cost(1), Clay + Clay)
  val SAWMILL = new RawMaterialCard("SAWMILL", new Cost(1), Wood + Wood)

  ////
  // AGE III
  ////

  // Commercial Cards
  // TODO: Change Arena to give points for built stages of a wonder
  val ARENA = RewardCommercialCard("ARENA", Cost(0, MultiSet(Stone, Stone, Ore)), Set(), Some(ComplexReward(3, classOf[CommercialCard], Set(Self))), Some(ComplexReward(1, classOf[CommercialCard], Set(Self))))
  val CHAMBER_OF_COMMERCE = RewardCommercialCard("CHAMBER OF COMMERCE", Cost(0, MultiSet(Clay, Clay, Paper)), Set(), Some(ComplexReward(2, classOf[ManufacturedGoodCard], Set(Self))), Some(ComplexReward(2, classOf[ManufacturedGoodCard], Set(Self))))
  val LIGHTHOUSE = RewardCommercialCard("LIGHTHOUSE", Cost(0, MultiSet(Stone, Glass)), Set(), Some(ComplexReward(1, classOf[CommercialCard], Set(Self))), Some(ComplexReward(1, classOf[CommercialCard], Set(Self))))
  val HAVEN = RewardCommercialCard("HAVEN", Cost(0, MultiSet(Wood, Ore, Tapestry)), Set(), Some(ComplexReward(1, classOf[RawMaterialCard], Set(Self))), Some(ComplexReward(1, classOf[RawMaterialCard], Set(Self))))

  // Military Cards
  val CIRCUS = MilitaryCard("CIRCUS", Cost(0, MultiSet(Stone, Stone, Stone, Ore)), Set(), 3)
  val FORTIFICATIONS = MilitaryCard("FORTIFICATIONS", Cost(0, MultiSet(Ore, Ore, Ore, Stone)), Set(), 3)
  val ARSENAL = MilitaryCard("ARSENAL", Cost(0, MultiSet(Wood, Wood, Ore, Tapestry)), Set(), 3)
  val SIEGE_WORKSHOP = MilitaryCard("SIEGE WORKSHOP", Cost(0, MultiSet(Clay, Clay, Clay, Wood)), Set(), 3)

  // Science Cards
  val OBSERVATORY = ScienceCard("OBSERVATORY", Cost(0, MultiSet(Ore, Ore, Glass, Tapestry)), Set(), gear)
  val ACADEMY = ScienceCard("ACADEMY", Cost(0, MultiSet(Stone, Stone, Stone)), Set(), compass)
  val LODGE = ScienceCard("LODGE", Cost(0, MultiSet(Clay, Clay, Paper, Tapestry)), Set(), compass)
  val UNIVERSITY = ScienceCard("UNIVERSITY", Cost(0, MultiSet(Wood, Wood, Paper, Glass)), Set(), tablet)
  val STUDY = ScienceCard("STUDY", Cost(0, MultiSet(Wood, Paper, Tapestry)), Set(), gear)

  // Civilian Cards
  val TOWN_HALL = CivilianCard("TOWN HALL", Cost(0, MultiSet(Stone, Stone, Ore, Glass)), Set(), 6)
  val PALACE = CivilianCard("PALACE", Cost(0, MultiSet(Stone, Ore, Wood, Clay, Glass, Paper, Tapestry)), Set(), 8)
  val PANTHEON = CivilianCard("PANTHEON", Cost(0, MultiSet(Clay, Clay, Ore, Glass, Paper, Tapestry)), Set(), 7)
  val GARDENS = CivilianCard("GARDENS", Cost(0, MultiSet(Clay, Clay, Wood)), Set(), 5)
  val SENATE = CivilianCard("SENATE", Cost(0, MultiSet(Wood, Wood, Stone, Ore)), Set(), 6)

  // Guilds
  // TODO: Update victory point rewards for guilds
  val STRATEGISTS_GUILD = VictoryPointsGuildCard("STARTEGISTS GUILD", Cost(0, MultiSet(Ore, Ore, Stone, Tapestry)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  val TRADERS_GUILD = VictoryPointsGuildCard("TRADERS GUILD", Cost(0, MultiSet(Glass, Tapestry, Paper)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  val MAGISTRATES_GUILD = VictoryPointsGuildCard("MAGISTRATES GUILD", Cost(0, MultiSet(Wood, Wood, Wood, Stone, Tapestry)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  val SHOPOWNERS_GUILD = VictoryPointsGuildCard("SHOPOWNERS GUILD", Cost(0, MultiSet(Wood, Wood, Wood, Glass, Paper)), ComplexReward(1, classOf[RawMaterialCard], Set(Self)))
  val CRAFTMENS_GUILD = VictoryPointsGuildCard("CRAFTSMENS GUILD", Cost(0, MultiSet(Ore, Ore, Stone, Stone)), ComplexReward(2, classOf[RawMaterialCard], Set(Left, Right)))
  val WORKERS_GUILD = VictoryPointsGuildCard("WORKERS GUILD", Cost(0, MultiSet(Ore, Ore, Clay, Stone, Wood)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  val PHILOSOPHERS_GUILD = VictoryPointsGuildCard("PHILOSOPHERS GUILD", Cost(0, MultiSet(Clay, Clay, Clay, Paper, Tapestry)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  object SCIENTISTS_GUILD extends GuildCard("SCIENTISTS GUILD", Cost(0, MultiSet(Wood, Wood, Ore, Ore, Paper))) with HasScience {
    val value = gear | tablet | compass
  }
  val SPIES_GUILD = VictoryPointsGuildCard("SPIES GUILD", Cost(0, MultiSet(Clay, Clay, Clay, Glass)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Right)))
  val BUILDERS_GUILD = VictoryPointsGuildCard("BUILDERS GUILD", Cost(0, MultiSet(Stone, Stone, Clay, Clay, Glass)), ComplexReward(1, classOf[RawMaterialCard], Set(Left, Self, Right)))

  // Civilizations
  val RHODOS = Civilization("RHODOS", Ore)
  val ALEXANDRIA = Civilization("ALEXANDRIA", Glass)
  val HALIKARNASSOS = Civilization("HALIKARNASSOS", Tapestry)
  val OLYMPIA = Civilization("OLYMPIA", Wood)
  val GIZAH = Civilization("GIZAH", Stone)
  val EPHESOS = Civilization("EPHESOS", Paper)
  val BABYLON = Civilization("BABYLON", Clay)

  val civilizations = Set(RHODOS, ALEXANDRIA, HALIKARNASSOS, OLYMPIA, GIZAH, EPHESOS, BABYLON)

  // Game Setup
  val classicSevenWonders = GameSetup(
    Map(
      1 -> Map(
        3 -> MultiSet(APOTHECARY, CLAY_POOL, ORE_VEIN, WORKSHOP, SCRIPTORIUM, BARRACKS, EAST_TRADING_POST, STOCKADE, CLAY_PIT, LOOM, GLASSWORKS, THEATER, BATHS, TIMBER_YARD, PRESS, STONE_PIT, MARKETPLACE, GUARD_TOWER, WEST_TRADING_POST, ALTAR, LUMBER_YARD),
        4 -> MultiSet(GUARD_TOWER, LUMBER_YARD, PAWNSHOP, TAVERN, SCRIPTORIUM, EXCAVATION, ORE_VEIN),
        5 -> MultiSet(CLAY_POOL, ALTAR, APOTHECARY, BARRACKS, STONE_PIT, TAVERN, FOREST_CAVE),
        6 -> MultiSet(THEATER, PRESS, GLASSWORKS, LOOM, MARKETPLACE, MINE, TREE_FARM),
        7 -> MultiSet(WORKSHOP, EAST_TRADING_POST, STOCKADE, BATHS, WEST_TRADING_POST, TAVERN, PAWNSHOP)
      ),
      2 -> Map(
        3 -> MultiSet(CARAVANSERY, VINEYARD, STATUE, ARCHERY_RANGE, DISPENSARY, WALLS, FOUNDRY, LABORATORY, LIBRARY, STABLES, TEMPLE, AQUEDUCT, COURTHOUSE, FORUM, SCHOOL, GLASSWORKS, BRICKYARD, LOOM, QUARRY, SAWMILL, PRESS),
        4 -> MultiSet(BAZAR, TRAINING_GROUND, DISPENSARY, BRICKYARD, FOUNDRY, QUARRY, SAWMILL),
        5 -> MultiSet(GLASSWORKS, COURTHOUSE, LABORATORY, CARAVANSERY, STABLES, PRESS, LOOM),
        6 -> MultiSet(CARAVANSERY, FORUM, VINEYARD, ARCHERY_RANGE, LIBRARY, TEMPLE, TRAINING_GROUND),
        7 -> MultiSet(AQUEDUCT, STATUE, FORUM, BAZAR, SCHOOL, WALLS, TRAINING_GROUND)
      ),
      3 -> Map(
        3 -> MultiSet(LODGE, OBSERVATORY, SIEGE_WORKSHOP, ARENA, SENATE, ARSENAL, ACADEMY, TOWN_HALL, PANTHEON, PALACE, HAVEN, LIGHTHOUSE, UNIVERSITY, GARDENS, FORTIFICATIONS, STUDY),
        4 -> MultiSet(UNIVERSITY, ARSENAL, GARDENS, HAVEN, CIRCUS, CHAMBER_OF_COMMERCE),
        5 -> MultiSet(ARENA, TOWN_HALL, CIRCUS, SIEGE_WORKSHOP, SENATE),
        6 -> MultiSet(TOWN_HALL, CIRCUS, LODGE, PANTHEON, CHAMBER_OF_COMMERCE, LIGHTHOUSE),
        7 -> MultiSet(ARENA, OBSERVATORY, ACADEMY, FORTIFICATIONS, ARSENAL, PALACE)
      )
    ),
    Set(STRATEGISTS_GUILD, TRADERS_GUILD, MAGISTRATES_GUILD, SHOPOWNERS_GUILD, CRAFTMENS_GUILD, WORKERS_GUILD, PHILOSOPHERS_GUILD, SCIENTISTS_GUILD, SPIES_GUILD, BUILDERS_GUILD)
  )
}