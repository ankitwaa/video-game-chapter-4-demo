package in.kstream.demo.message.revision;

import in.kstream.demo.message.Player;
import in.kstream.demo.message.Products;
import in.kstream.demo.message.ScoreDetails;
import in.kstream.demo.message.ScoreEvent;
import in.kstream.demo.message.serdeFactory.JsonSerdes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.HashMap;
import java.util.Map;


public class Application {

    public static void main(String[] args) {
        System.out.println("lag gaye....");
        Map<String, Object> properties = new HashMap<>();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "consumer_group_7");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        StreamsConfig config = new StreamsConfig(properties);

        StreamsBuilder streamsBuilder = new StreamsBuilder();
        KStream<String, ScoreEvent> scoreStreams = streamsBuilder.stream("score-events", Consumed.with(Serdes.String(), JsonSerdes.scoreEventSerde())).
                map((k,v) -> KeyValue.pair(v.getPlayerId(),v));

        KTable<String, Player> kTableProduct = streamsBuilder.table("players", Consumed.with(Serdes.String(), JsonSerdes.playerSerde()));
        GlobalKTable<String, Products> globalKTableProduct = streamsBuilder.globalTable("products", Consumed.with(Serdes.String(), JsonSerdes.productsSerde()));

        KStream<String, ScoreDetails> joinScoreDetails =  scoreStreams.leftJoin(kTableProduct, (lv, rv) -> {
            System.out.println("Mapping............");
            ScoreDetails scoreDetail = new ScoreDetails();
            scoreDetail.setScoreEvent(lv);
            scoreDetail.setPlayer(rv);
            return scoreDetail;
        }, Joined.with(Serdes.String(), JsonSerdes.scoreEventSerde(), JsonSerdes.playerSerde()));

        // joinScoreDetails.foreach((k,v) -> System.out.println("Joinded....player="+ v.getPlayer() + ", score=" + v.getScoreEvent()));
        // scoreStreams.foreach((k,v) -> System.out.println("Score Event....key"+ k + ", value=" + v));

       KStream<String, ScoreDetails>[] branches =   joinScoreDetails.branch(((key, value) -> value.getPlayer() ==null),((key, value) -> value.getPlayer() != null));

        // No value presents
        branches[0].foreach((k,v) -> {
            System.out.println("No right" + v.getPlayer());
        });

        // Value is presents
        branches[1].foreach((k,v) -> {
            System.out.println("Yes right" + v.getPlayer());
        });

        Topology topology = streamsBuilder.build();
        KafkaStreams kafkaStreams = new KafkaStreams(topology, config);
        kafkaStreams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> kafkaStreams.close()));
        System.out.println("Describe-------"+ topology.describe());
        ;
    }
}
